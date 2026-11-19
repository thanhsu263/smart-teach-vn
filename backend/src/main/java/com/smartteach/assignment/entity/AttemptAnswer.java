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

/**
 * The saved answer to one question, as JSON text: an option index ({@code 2}), a list of indexes
 * ({@code [0,3]}) or a string. {@code correct} stays null until the attempt is submitted.
 */
@Entity
@Table(name = "attempt_answers")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttemptAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempt_id", nullable = false, updatable = false)
    private Long attemptId;

    @Column(nullable = false, updatable = false)
    private int position;

    @Column(nullable = false, length = 1000)
    private String answer;

    @Column(name = "is_correct")
    private Boolean correct;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    public AttemptAnswer(Long attemptId, int position, String answer, Instant now) {
        this.attemptId = attemptId;
        this.position = position;
        this.answer = answer;
        this.savedAt = now;
    }
}
