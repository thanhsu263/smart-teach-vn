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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A quiz given to a class. On publish the quiz is copied into {@code quizSnapshot} (JSON), so later
 * edits or deletion of the quiz never change what students answer or how they were graded.
 */
@Entity
@Table(name = "assignments")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    /** Null once the quiz is deleted; a published assignment still has its snapshot. */
    @Column(name = "quiz_id")
    private Long quizId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 1000)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quiz_snapshot")
    private String quizSnapshot;

    @Column(name = "public_code", nullable = false, length = 10, updatable = false)
    private String publicCode;

    @Column(name = "opens_at")
    private Instant opensAt;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "reveal_policy", nullable = false, length = 20)
    private RevealPolicy revealPolicy;

    @Column(nullable = false, length = 20)
    private AssignmentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    public Assignment(Long ownerId, String publicCode, Instant now) {
        this.ownerId = ownerId;
        this.publicCode = publicCode;
        this.status = AssignmentStatus.DRAFT;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Students can start, answer and submit only while this is true. */
    public boolean isOpen(Instant now) {
        return status == AssignmentStatus.PUBLISHED
                && (opensAt == null || !opensAt.isAfter(now))
                && (dueAt == null || dueAt.isAfter(now));
    }

    /** Closed by the teacher or past the deadline: nobody can answer any more. */
    public boolean isEnded(Instant now) {
        return status == AssignmentStatus.CLOSED || (dueAt != null && !dueAt.isAfter(now));
    }
}
