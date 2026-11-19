package com.smartteach.quiz.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code correct} may be left out (= false). Boolean, not boolean: Jackson 3 rejects a missing primitive. */
public record OptionRequest(@NotBlank @Size(max = 150) String label, Boolean correct) {

    public OptionRequest {
        correct = Boolean.TRUE.equals(correct);
    }
}
