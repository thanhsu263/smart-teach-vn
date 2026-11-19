package com.smartteach.classroom.dto;

import java.time.Instant;

public record StudentResponse(Long id, String name, int position, String codeHint, Instant createdAt) {
}
