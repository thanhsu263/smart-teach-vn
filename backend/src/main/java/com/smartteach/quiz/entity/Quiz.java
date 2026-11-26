package com.smartteach.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Root of the quiz aggregate. Questions are not mapped as a collection here: the service always
 * reads and replaces them as a whole (see QuizService), which keeps the number of queries fixed.
 */
@Entity
@Table(name = "quizzes")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Bumped by Hibernate on every update; clients send back the version they loaded. */
    @Version
    @Setter(AccessLevel.NONE)
    private long version;

    public Quiz(Long ownerId, String title, Instant now) {
        this.ownerId = ownerId;
        this.title = title;
        this.createdAt = now;
        this.updatedAt = now;
    }
}
