package com.smartteach.live.dto;

import com.smartteach.live.service.LiveStatus;
import com.smartteach.quiz.entity.QuestionKind;
import java.time.Instant;
import java.util.List;

/**
 * Everything the host screen and the players see, pushed to {@code /topic/live/{pin}} after every
 * change. It never contains correct answers while a question is open.
 *
 * @param version    increases with every change; clients ignore a state older than the one they have
 * @param serverTime lets clients correct their own clock for the countdown
 * @param question   the current question; null in the lobby and after the game ended
 * @param players    ranked by score (join order in the lobby)
 */
public record LiveState(String pin, String quizTitle, LiveStatus status, long version, Instant serverTime,
                        int questionCount, Question question, int answeredCount, List<Player> players) {

    public record Question(int position, QuestionKind kind, String prompt, int timeLimitSeconds, Instant endsAt,
                           List<String> options, Reveal reveal) {
    }

    /** Only in the reveal phase. {@code optionCounts}: how many players chose each option. */
    public record Reveal(List<Integer> correctOptions, List<String> acceptedAnswers, List<Integer> optionCounts,
                         int correctCount) {
    }

    /** {@code lastPoints}: points won on the current question, only in the reveal phase; null if the player did not answer. */
    public record Player(int id, String nickname, int score, int rank, Integer lastPoints) {
    }
}
