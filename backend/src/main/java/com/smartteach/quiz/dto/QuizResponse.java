package com.smartteach.quiz.dto;

import java.time.Instant;
import java.util.List;

public record QuizResponse(Long id, String title, Instant createdAt, Instant updatedAt,
                           List<QuestionResponse> questions) {
}
