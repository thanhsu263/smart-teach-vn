package com.smartteach.user.dto;

import jakarta.validation.constraints.NotNull;

/** {@code emailNotifications}: assignment reminders and summaries by email. */
public record SettingsRequest(@NotNull Boolean emailNotifications) {
}
