package com.smartteach.assignment.dto;

import com.smartteach.assignment.entity.RevealPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Optional fields: description (empty), opensAt / dueAt (no limit), revealPolicy (after_close). */
public record AssignmentRequest(
        @NotNull Long classId,
        @NotNull Long quizId,
        @NotBlank @Size(max = 100) String title,
        @Size(max = 1000) String description,
        Instant opensAt,
        Instant dueAt,
        RevealPolicy revealPolicy) {

    public AssignmentRequest {
        description = description == null ? "" : description.strip();
        revealPolicy = revealPolicy == null ? RevealPolicy.AFTER_CLOSE : revealPolicy;
    }
}
