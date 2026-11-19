package com.smartteach.assignment.dto;

import com.smartteach.assignment.entity.AssignmentStatus;
import java.time.Instant;

public record AssignmentSummaryResponse(Long id, Long classId, String className, String title, String publicCode,
                                        AssignmentStatus status, Instant opensAt, Instant dueAt, Instant createdAt) {
}
