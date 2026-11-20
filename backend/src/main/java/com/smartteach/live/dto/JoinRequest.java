package com.smartteach.live.dto;

import jakarta.validation.constraints.NotNull;

/** The nickname rules (length, spaces, unique in the room) are checked by the room itself. */
public record JoinRequest(@NotNull String nickname) {
}
