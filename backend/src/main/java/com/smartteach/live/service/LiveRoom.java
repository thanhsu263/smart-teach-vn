package com.smartteach.live.service;

import com.smartteach.common.exception.ApiException;
import com.smartteach.live.dto.LiveState;
import com.smartteach.live.dto.PlayerView;
import com.smartteach.quiz.dto.OptionResponse;
import com.smartteach.quiz.dto.QuestionResponse;
import com.smartteach.quiz.entity.QuestionKind;
import com.smartteach.quiz.service.Grading;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import tools.jackson.databind.JsonNode;

/**
 * One live game, kept in memory: players, the current question and the scores. Players, the
 * teacher and the question timer all change the same room from different threads, so every
 * public method is {@code synchronized}. The room never reads the clock itself; the caller passes
 * {@code now}, which keeps the rules easy to test.
 */
class LiveRoom {

    static final int MAX_PLAYERS = 50;
    static final int MAX_NICKNAME = 20;
    static final int FULL_POINTS = 1000;
    static final int MIN_POINTS = 500;
    static final Duration KEEP_AFTER_END = Duration.ofMinutes(15);
    static final Duration KEEP_IDLE = Duration.ofHours(3);

    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cntrl}");

    private final String pin;
    private final Long ownerId;
    private final String quizTitle;
    private final List<QuestionResponse> questions;

    /** Key: SHA-256 of the player token. Insertion order = join order. */
    private final Map<String, Player> players = new LinkedHashMap<>();
    /** Lowercase nicknames, including removed players, so a removed player cannot come back under the same name. */
    private final Set<String> takenNicknames = new HashSet<>();
    private int nextPlayerId = 1;

    private LiveStatus status = LiveStatus.LOBBY;
    private int position = -1;
    private Instant openedAt;
    private Instant endsAt;
    private long version;
    private Instant changedAt;

    LiveRoom(String pin, Long ownerId, String quizTitle, List<QuestionResponse> questions, Instant now) {
        this.pin = pin;
        this.ownerId = ownerId;
        this.quizTitle = quizTitle;
        this.questions = List.copyOf(questions);
        this.changedAt = now;
    }

    String pin() {
        return pin;
    }

    Long ownerId() {
        return ownerId;
    }

    synchronized LiveStatus status() {
        return status;
    }

    synchronized JoinedPlayer join(String rawNickname, String tokenHash, Instant now) {
        if (status != LiveStatus.LOBBY) {
            throw ApiException.conflict("The game has already started");
        }
        String nickname = SPACES.matcher(rawNickname.strip()).replaceAll(" ");
        if (nickname.isEmpty() || nickname.length() > MAX_NICKNAME || CONTROL_CHARS.matcher(nickname).find()) {
            throw ApiException.badRequest("The nickname must have 1 to " + MAX_NICKNAME + " characters");
        }
        if (players.size() >= MAX_PLAYERS) {
            throw ApiException.conflict("The room is full");
        }
        if (!takenNicknames.add(nickname.toLowerCase(Locale.ROOT))) {
            throw ApiException.conflict("The nickname is already taken");
        }
        Player player = new Player(nextPlayerId++, nickname);
        players.put(tokenHash, player);
        changed(now);
        return new JoinedPlayer(player.id, player.nickname);
    }

    /** The teacher's single "next" button: start, show the answer early, go to the next question, finish. */
    synchronized void next(Instant now) {
        switch (status) {
            case LOBBY -> {
                if (players.isEmpty()) {
                    throw ApiException.conflict("Wait for at least one player");
                }
                open(0, now);
            }
            case QUESTION -> reveal();
            case REVEAL -> {
                if (position + 1 < questions.size()) {
                    open(position + 1, now);
                } else {
                    status = LiveStatus.ENDED;
                }
            }
            case ENDED -> throw ApiException.conflict("The game has ended");
        }
        changed(now);
    }

    /** Called by the timer. Does nothing if the question was already closed or the game moved on. */
    synchronized boolean timeUp(int questionPosition, Instant now) {
        if (status != LiveStatus.QUESTION || position != questionPosition) {
            return false;
        }
        reveal();
        changed(now);
        return true;
    }

    synchronized void end(Instant now) {
        if (status == LiveStatus.ENDED) {
            return;
        }
        if (status == LiveStatus.QUESTION) {
            reveal(); // answers already sent still count
        }
        status = LiveStatus.ENDED;
        changed(now);
    }

    synchronized void answer(String tokenHash, int questionPosition, JsonNode answer, Instant now) {
        Player player = player(tokenHash);
        if (status != LiveStatus.QUESTION || position != questionPosition || !now.isBefore(endsAt)) {
            throw ApiException.conflict("The question is closed");
        }
        if (player.answers.containsKey(position)) {
            throw ApiException.conflict("Already answered");
        }
        QuestionResponse question = questions.get(position);
        String problem = Grading.checkShape(question, answer);
        if (problem != null) {
            throw ApiException.badRequest(problem);
        }
        long responseMs = Duration.between(openedAt, now).toMillis();
        player.answers.put(position, new Answer(answer, responseMs));
        // Everyone answered: no need to wait for the timer.
        if (players.values().stream().allMatch(p -> p.answers.containsKey(position))) {
            reveal();
        }
        changed(now);
    }

    synchronized void remove(int playerId, Instant now) {
        boolean removed = players.values().removeIf(p -> p.id == playerId);
        if (!removed) {
            throw ApiException.notFound("Player not found");
        }
        changed(now);
    }

    synchronized PlayerView playerView(String tokenHash) {
        Player player = player(tokenHash);
        boolean answered = status == LiveStatus.QUESTION && player.answers.containsKey(position);
        return new PlayerView(player.id, player.nickname, player.score, answered);
    }

    /** Ended rooms stay a while so players can see the final ranking; idle rooms are dropped later. */
    synchronized boolean isExpired(Instant now) {
        Duration keep = status == LiveStatus.ENDED ? KEEP_AFTER_END : KEEP_IDLE;
        return now.isAfter(changedAt.plus(keep));
    }

    synchronized LiveState state(Instant now) {
        LiveState.Question question = status == LiveStatus.QUESTION || status == LiveStatus.REVEAL
                ? questionState()
                : null;
        int answered = status == LiveStatus.QUESTION || status == LiveStatus.REVEAL
                ? (int) players.values().stream().filter(p -> p.answers.containsKey(position)).count()
                : 0;
        return new LiveState(pin, quizTitle, status, version, now, questions.size(), question, answered, ranking());
    }

    /**
     * Correct: 1000 points, minus up to half for the time used (answering at the last moment gives
     * 500). Wrong or no answer: 0.
     */
    static int points(long responseMs, long limitMs) {
        long points = Math.round(FULL_POINTS * (1 - 0.5 * responseMs / limitMs));
        return Math.clamp(points, MIN_POINTS, FULL_POINTS);
    }

    private void open(int questionPosition, Instant now) {
        position = questionPosition;
        openedAt = now;
        endsAt = now.plusSeconds(questions.get(position).timeLimitSeconds());
        status = LiveStatus.QUESTION;
    }

    /** Grades the open question once and moves to the reveal phase. */
    private void reveal() {
        QuestionResponse question = questions.get(position);
        long limitMs = question.timeLimitSeconds() * 1000L;
        for (Player player : players.values()) {
            Answer answer = player.answers.get(position);
            if (answer != null && Grading.isCorrect(question, answer.value)) {
                answer.points = points(answer.responseMs, limitMs);
                player.score += answer.points;
            }
        }
        status = LiveStatus.REVEAL;
    }

    private LiveState.Question questionState() {
        QuestionResponse q = questions.get(position);
        List<String> options = q.options().stream().map(OptionResponse::label).toList();
        LiveState.Reveal reveal = status == LiveStatus.REVEAL ? revealState(q) : null;
        return new LiveState.Question(position, q.kind(), q.prompt(), q.timeLimitSeconds(), endsAt, options, reveal);
    }

    private LiveState.Reveal revealState(QuestionResponse q) {
        List<Integer> correctOptions = IntStream.range(0, q.options().size())
                .filter(i -> q.options().get(i).correct()).boxed().toList();
        int[] counts = new int[q.options().size()];
        int correctCount = 0;
        for (Player player : players.values()) {
            Answer answer = player.answers.get(position);
            if (answer == null) {
                continue;
            }
            if (answer.points > 0) {
                correctCount++;
            }
            if (q.kind() == QuestionKind.MULTI) {
                answer.value.forEach(index -> counts[index.intValue()]++);
            } else if (q.kind() != QuestionKind.SHORT_TEXT) {
                counts[answer.value.intValue()]++;
            }
        }
        return new LiveState.Reveal(correctOptions, q.acceptedAnswers(), IntStream.of(counts).boxed().toList(),
                correctCount);
    }

    /** Highest score first, ties share a rank (1, 1, 3) and keep the join order. */
    private List<LiveState.Player> ranking() {
        List<Player> sorted = new ArrayList<>(players.values());
        sorted.sort(Comparator.comparingInt((Player p) -> p.score).reversed()); // stable: join order for ties
        boolean showPoints = status == LiveStatus.REVEAL;
        Map<Integer, Integer> rankOfScore = new HashMap<>();
        List<LiveState.Player> result = new ArrayList<>();
        for (Player p : sorted) {
            int rank = rankOfScore.computeIfAbsent(p.score, score -> result.size() + 1);
            Answer answer = p.answers.get(position);
            Integer lastPoints = showPoints && answer != null ? answer.points : null;
            result.add(new LiveState.Player(p.id, p.nickname, p.score, rank, lastPoints));
        }
        return Collections.unmodifiableList(result);
    }

    private Player player(String tokenHash) {
        Player player = tokenHash == null ? null : players.get(tokenHash);
        if (player == null) {
            throw ApiException.notFound("Player not found");
        }
        return player;
    }

    private void changed(Instant now) {
        version++;
        changedAt = now;
    }

    record JoinedPlayer(int id, String nickname) {
    }

    private static final class Player {
        final int id;
        final String nickname;
        final Map<Integer, Answer> answers = new HashMap<>();
        int score;

        Player(int id, String nickname) {
            this.id = id;
            this.nickname = nickname;
        }
    }

    private static final class Answer {
        final JsonNode value;
        final long responseMs;
        int points;

        Answer(JsonNode value, long responseMs) {
            this.value = value;
            this.responseMs = responseMs;
        }
    }
}
