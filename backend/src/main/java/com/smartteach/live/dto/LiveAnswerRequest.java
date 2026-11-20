package com.smartteach.live.dto;

import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

/**
 * {@code position} must be the question that is open now, so a late answer cannot land on the next
 * question. {@code answer} has the same shape as in assignments: an option index, a list of
 * indexes (multi) or a text (short_text).
 */
public record LiveAnswerRequest(@NotNull Integer position, @NotNull JsonNode answer) {
}
