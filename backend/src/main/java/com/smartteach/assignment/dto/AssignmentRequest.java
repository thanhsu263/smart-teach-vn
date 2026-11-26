package com.smartteach.assignment.dto;

import com.smartteach.assignment.entity.RevealPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Optional fields: description (empty), opensAt / dueAt (no limit), revealPolicy (after_close).
 * {@code version} is the one the client loaded; when it is stale the update is refused with 409.
 */
public record AssignmentRequest(
        @NotNull Long classId,
        @NotNull Long quizId,
        @NotBlank @Size(max = 100) String title,
        @Size(max = 1000) String description,
        Instant opensAt,
        Instant dueAt,
        RevealPolicy revealPolicy,
        Long version) {

    public AssignmentRequest {
        description = description == null ? "" : description.strip();
        revealPolicy = revealPolicy == null ? RevealPolicy.AFTER_CLOSE : revealPolicy;
    }

    /** For creating, where there is no version yet. */
    public AssignmentRequest(Long classId, Long quizId, String title, String description,
                             Instant opensAt, Instant dueAt, RevealPolicy revealPolicy) {
        this(classId, quizId, title, description, opensAt, dueAt, revealPolicy, null);
    }
}
