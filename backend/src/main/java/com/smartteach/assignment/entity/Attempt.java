package com.smartteach.assignment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One student's work on one assignment. Score, counts and submittedAt are set on submit. */
@Entity
@Table(name = "attempts")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assignment_id", nullable = false, updatable = false)
    private Long assignmentId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Long studentId;

    @Column(nullable = false, length = 20)
    private AttemptStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    private Integer score;

    @Column(name = "correct_count")
    private Integer correctCount;

    @Column(name = "question_total")
    private Integer questionTotal;

    /** SHA-256 of the attempt token given to the student's browser. */
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    public Attempt(Long assignmentId, Long studentId, Instant now) {
        this.assignmentId = assignmentId;
        this.studentId = studentId;
        this.status = AttemptStatus.IN_PROGRESS;
        this.startedAt = now;
    }
}
