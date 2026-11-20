package com.smartteach.live.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartteach.common.exception.ApiException;
import com.smartteach.live.dto.LiveState;
import com.smartteach.quiz.dto.OptionResponse;
import com.smartteach.quiz.dto.QuestionResponse;
import com.smartteach.quiz.entity.QuestionKind;
import java.time.Instant;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class LiveRoomTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Instant T0 = Instant.parse("2030-01-01T08:00:00Z");

    /** Q0: single (A correct), 20 s. Q1: multi (0 and 2 correct), 30 s. Q2: short text, 10 s. */
    private static final List<QuestionResponse> QUESTIONS = List.of(
            new QuestionResponse(0, QuestionKind.SINGLE, "Thủ đô?", 20, false,
                    List.of(new OptionResponse("Hà Nội", true), new OptionResponse("Huế", false)), List.of()),
            new QuestionResponse(1, QuestionKind.MULTI, "Giáp biển?", 30, false,
                    List.of(new OptionResponse("Đà Nẵng", true), new OptionResponse("Gia Lai", false),
                            new OptionResponse("Khánh Hoà", true)), List.of()),
            new QuestionResponse(2, QuestionKind.SHORT_TEXT, "Đỉnh núi cao nhất?", 10, true,
                    List.of(), List.of("Phan Xi Păng")));

    private final LiveRoom room = new LiveRoom("123456", 7L, "Địa lý", QUESTIONS, T0);

    @Test
    void pointsDependOnSpeed() {
        assertThat(LiveRoom.points(0, 20_000)).isEqualTo(1000);
        assertThat(LiveRoom.points(10_000, 20_000)).isEqualTo(750);
        assertThat(LiveRoom.points(19_999, 20_000)).isEqualTo(500);
        assertThat(LiveRoom.points(25_000, 20_000)).isEqualTo(500);
    }

    @Test
    void nicknamesAreTrimmedAndUniqueIgnoringCase() {
        assertThat(room.join("  Minh   Anh ", "t1", T0).nickname()).isEqualTo("Minh Anh");
        assertStatus(() -> room.join("minh anh", "t2", T0), HttpStatus.CONFLICT);
        assertStatus(() -> room.join("   ", "t2", T0), HttpStatus.BAD_REQUEST);
        assertStatus(() -> room.join("a".repeat(21), "t2", T0), HttpStatus.BAD_REQUEST);
        assertStatus(() -> room.join("a\u0007b", "t2", T0), HttpStatus.BAD_REQUEST);
        assertThat(room.join("a".repeat(20), "t2", T0).id()).isEqualTo(2);
    }

    @Test
    void roomIsLimitedTo50Players() {
        for (int i = 0; i < LiveRoom.MAX_PLAYERS; i++) {
            room.join("p" + i, "t" + i, T0);
        }
        assertStatus(() -> room.join("late", "late", T0), HttpStatus.CONFLICT);
    }

    @Test
    void removedPlayerCannotComeBackWithTheSameNickname() {
        int id = room.join("Bình", "t1", T0).id();
        room.remove(id, T0);
        assertStatus(() -> room.join("bình", "t2", T0), HttpStatus.CONFLICT);
        assertStatus(() -> room.playerView("t1"), HttpStatus.NOT_FOUND);
        assertStatus(() -> room.remove(id, T0), HttpStatus.NOT_FOUND);
    }

    @Test
    void cannotStartWithoutPlayersOrJoinAfterStart() {
        assertStatus(() -> room.next(T0), HttpStatus.CONFLICT);
        room.join("An", "t1", T0);
        room.next(T0);
        assertThat(room.status()).isEqualTo(LiveStatus.QUESTION);
        assertStatus(() -> room.join("Bình", "t2", T0), HttpStatus.CONFLICT);
    }

    @Test
    void openQuestionHidesTheAnswer() {
        room.join("An", "t1", T0);
        room.next(T0);
        LiveState state = room.state(T0);
        assertThat(state.question().prompt()).isEqualTo("Thủ đô?");
        assertThat(state.question().options()).containsExactly("Hà Nội", "Huế");
        assertThat(state.question().endsAt()).isEqualTo(T0.plusSeconds(20));
        assertThat(state.question().reveal()).isNull();
        assertThat(state.players().getFirst().lastPoints()).isNull();
    }

    @Test
    void fullGameScoresBySpeedAndRanksWithTies() {
        room.join("An", "a", T0);
        room.join("Bình", "b", T0);
        room.join("Chi", "c", T0);
        long v0 = room.state(T0).version();

        // Q0: An right at 0 s, Bình right at 10 s, Chi wrong. Everyone answered → reveal at once.
        room.next(T0);
        room.answer("a", 0, json("0"), T0);
        room.answer("b", 0, json("0"), T0.plusSeconds(10));
        assertThat(room.status()).isEqualTo(LiveStatus.QUESTION);
        assertThat(room.state(T0).answeredCount()).isEqualTo(2);
        room.answer("c", 0, json("1"), T0.plusSeconds(10));
        LiveState reveal = room.state(T0.plusSeconds(10));
        assertThat(reveal.status()).isEqualTo(LiveStatus.REVEAL);
        assertThat(reveal.version()).isGreaterThan(v0);
        assertThat(reveal.question().reveal().correctOptions()).containsExactly(0);
        assertThat(reveal.question().reveal().optionCounts()).containsExactly(2, 1);
        assertThat(reveal.question().reveal().correctCount()).isEqualTo(2);
        assertThat(reveal.players()).extracting(LiveState.Player::nickname).containsExactly("An", "Bình", "Chi");
        assertThat(reveal.players()).extracting(LiveState.Player::lastPoints).containsExactly(1000, 750, 0);

        // Q1: only Bình answers (right, 15 s of 30 → 750), then the teacher reveals early.
        Instant q1 = T0.plusSeconds(60);
        room.next(q1);
        room.answer("b", 1, json("[2, 0]"), q1.plusSeconds(15));
        assertThat(room.timeUp(0, q1.plusSeconds(16))).isFalse(); // stale timer of Q0
        room.next(q1.plusSeconds(16));
        assertThat(room.state(q1).status()).isEqualTo(LiveStatus.REVEAL);
        assertThat(room.state(q1).question().reveal().optionCounts()).containsExactly(1, 0, 1);
        assertThat(room.state(q1).players()).extracting(LiveState.Player::score).containsExactly(1500, 1000, 0);
        // No answer is not the same as a wrong answer: the player screen says "time is up".
        assertThat(room.state(q1).players()).extracting(LiveState.Player::lastPoints).containsExactly(750, null, null);

        // Q2: short text without diacritics, An answers at the last moment (500) and ties with Bình.
        Instant q2 = q1.plusSeconds(60);
        room.next(q2);
        room.answer("a", 2, json("\"phan xi pang\""), q2.plusMillis(9_999));
        assertThat(room.timeUp(2, q2.plusSeconds(10))).isTrue();
        LiveState last = room.state(q2);
        assertThat(last.question().reveal().acceptedAnswers()).containsExactly("Phan Xi Păng");
        assertThat(last.players()).extracting(LiveState.Player::nickname).containsExactly("An", "Bình", "Chi");
        assertThat(last.players()).extracting(LiveState.Player::rank).containsExactly(1, 1, 3);
        assertThat(room.playerView("a").score()).isEqualTo(1500);

        room.next(q2.plusSeconds(5));
        assertThat(room.state(q2).status()).isEqualTo(LiveStatus.ENDED);
        assertThat(room.state(q2).question()).isNull();
        assertStatus(() -> room.next(q2.plusSeconds(6)), HttpStatus.CONFLICT);
    }

    @Test
    void answersMustBeOnTimeForTheOpenQuestionAndOnlyOnce() {
        room.join("An", "a", T0);
        room.join("Bình", "b", T0);
        assertStatus(() -> room.answer("a", 0, json("0"), T0), HttpStatus.CONFLICT); // lobby
        room.next(T0);
        assertStatus(() -> room.answer("x", 0, json("0"), T0), HttpStatus.NOT_FOUND);
        assertStatus(() -> room.answer(null, 0, json("0"), T0), HttpStatus.NOT_FOUND);
        assertStatus(() -> room.answer("a", 1, json("0"), T0), HttpStatus.CONFLICT);
        assertStatus(() -> room.answer("a", 0, json("5"), T0), HttpStatus.BAD_REQUEST);
        assertStatus(() -> room.answer("a", 0, json("0"), T0.plusSeconds(20)), HttpStatus.CONFLICT);
        room.answer("a", 0, json("0"), T0.plusSeconds(1));
        assertThat(room.playerView("a").answered()).isTrue();
        assertStatus(() -> room.answer("a", 0, json("1"), T0.plusSeconds(2)), HttpStatus.CONFLICT);
    }

    @Test
    void endingDuringAQuestionStillCountsSentAnswers() {
        room.join("An", "a", T0);
        room.join("Bình", "b", T0);
        room.next(T0);
        room.answer("a", 0, json("0"), T0);
        room.end(T0.plusSeconds(1));
        room.end(T0.plusSeconds(2)); // no-op
        assertThat(room.status()).isEqualTo(LiveStatus.ENDED);
        assertThat(room.playerView("a").score()).isEqualTo(1000);
        assertThat(room.timeUp(0, T0.plusSeconds(20))).isFalse();
    }

    @Test
    void endedRoomsExpireSoonerThanIdleOnes() {
        assertThat(room.isExpired(T0.plus(LiveRoom.KEEP_IDLE))).isFalse();
        assertThat(room.isExpired(T0.plus(LiveRoom.KEEP_IDLE).plusSeconds(1))).isTrue();
        room.end(T0);
        assertThat(room.isExpired(T0.plus(LiveRoom.KEEP_AFTER_END).plusSeconds(1))).isTrue();
    }

    private static JsonNode json(String value) {
        return JSON.readTree(value);
    }

    private static void assertStatus(ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(status));
    }
}
