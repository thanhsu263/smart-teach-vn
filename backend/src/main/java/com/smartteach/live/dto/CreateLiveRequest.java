package com.smartteach.live.dto;

import jakarta.validation.constraints.NotNull;

public record CreateLiveRequest(@NotNull Long quizId) {
}
