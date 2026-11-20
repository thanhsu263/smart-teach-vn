package com.smartteach.live.dto;

/** {@code token} identifies the player in later calls ({@code X-Player-Token}); it is shown only here. */
public record JoinResponse(int playerId, String nickname, String token) {
}
