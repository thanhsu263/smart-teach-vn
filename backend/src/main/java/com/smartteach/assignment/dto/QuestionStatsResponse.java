package com.smartteach.assignment.dto;

import com.smartteach.quiz.entity.QuestionKind;
import java.util.List;

/**
 * Item analysis of an assignment, from submitted attempts only. {@code groupSize} is the number of
 * attempts in the top and in the bottom group (27% of {@code submittedCount}); it is 0 and every
 * {@code discrimination} is null while there are fewer than {@code minSubmissions}.
 */
public record QuestionStatsResponse(int submittedCount, int minSubmissions, int groupSize,
                                    List<QuestionStat> questions) {

    /**
     * {@code correctRate} is a percentage (null without submissions); {@code discrimination} is
     * (correct in top group - correct in bottom group) / groupSize, from -1 to 1.
     */
    public record QuestionStat(int position, QuestionKind kind, String prompt, int correct, Double correctRate,
                               Double discrimination, WrongAnswer mostCommonWrongAnswer, boolean tooHard,
                               boolean lowDiscrimination) {
    }

    /** The answer as the student would read it: option labels, or the text typed. */
    public record WrongAnswer(String answer, int count) {
    }
}
