package com.smartteach.classroom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StudentRequest(@NotBlank @Size(max = 100) String name) {
}
