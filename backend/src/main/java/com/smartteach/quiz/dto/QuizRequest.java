package com.smartteach.quiz.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The whole quiz: saving replaces every question. Rules that depend on the kind are in QuizRules.
 * {@code version} is the one the client loaded; when it is stale the update is refused with 409.
 */
public record QuizRequest(
        @NotBlank @Size(max = 100) String title,
        @Size(max = 50, message = "A quiz can have at most 50 questions")
        List<@NotNull @Valid QuestionRequest> questions,
        Long version) {

    public QuizRequest {
        questions = questions == null ? List.of() : questions;
    }

    /** For creating, where there is no version yet. */
    public QuizRequest(String title, List<QuestionRequest> questions) {
        this(title, questions, null);
    }
}
