package com.smartteach.live.dto;

/** What a player needs after reloading the page. {@code answered}: already answered the current question. */
public record PlayerView(int id, String nickname, int score, boolean answered) {
}
