package com.smartteach.quiz.dto;

import java.time.Instant;

public record QuizSummaryResponse(Long id, String title, long questionCount, Instant createdAt, Instant updatedAt) {
}
