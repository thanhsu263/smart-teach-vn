package com.smartteach.assignment.dto;

import com.smartteach.assignment.entity.AssignmentStatus;
import com.smartteach.assignment.entity.RevealPolicy;
import java.time.Instant;

/** {@code questionCount} is null for a draft: the questions are only fixed when it is published. */
public record AssignmentResponse(Long id, Long classId, String className, Long quizId, String title,
                                 String description, String publicCode, Instant opensAt, Instant dueAt,
                                 RevealPolicy revealPolicy, AssignmentStatus status, Integer questionCount,
                                 Instant createdAt, Instant updatedAt, Instant closedAt) {
}
