package com.smartteach.assignment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StartAttemptRequest(@NotBlank @Size(max = 50) String accessCode) {
}
