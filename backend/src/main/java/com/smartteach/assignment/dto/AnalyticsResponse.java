package com.smartteach.assignment.dto;

import java.time.Instant;
import java.util.List;

/** Built from submitted attempts only; {@code averageScore} is null while nobody has submitted. */
public record AnalyticsResponse(int studentCount, int submittedCount, int inProgressCount, Double averageScore,
                                List<QuestionStats> questions, List<StudentProgress> students) {

    public record QuestionStats(int position, String prompt, int answered, int correct, Double correctRate) {
    }

    /** {@code status} is not_started, in_progress or submitted. */
    public record StudentProgress(Long studentId, String name, String status, Integer score, Integer correctCount,
                                  Instant submittedAt) {
    }
}
