package com.smartteach.classroom.dto;

import java.time.Instant;

public record ClassResponse(Long id, String name, long studentCount, Instant createdAt) {
}
