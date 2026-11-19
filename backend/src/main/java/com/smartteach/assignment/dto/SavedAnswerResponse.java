package com.smartteach.assignment.dto;

import java.time.Instant;

/** {@code savedAt} is null when the answer was cleared. */
public record SavedAnswerResponse(int position, Instant savedAt) {
}
