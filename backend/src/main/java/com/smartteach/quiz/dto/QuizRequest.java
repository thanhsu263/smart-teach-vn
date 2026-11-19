package com.smartteach.quiz.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The whole quiz: saving replaces every question. Rules that depend on the kind are in QuizRules. */
public record QuizRequest(
        @NotBlank @Size(max = 100) String title,
        @Size(max = 50, message = "A quiz can have at most 50 questions")
        List<@NotNull @Valid QuestionRequest> questions) {

    public QuizRequest {
        questions = questions == null ? List.of() : questions;
    }
}
