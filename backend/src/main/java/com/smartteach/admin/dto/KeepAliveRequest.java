package com.smartteach.admin.dto;

import jakarta.validation.constraints.NotNull;

public record KeepAliveRequest(@NotNull Boolean enabled) {
}
