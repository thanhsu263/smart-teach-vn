package com.smartteach.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A choice of a single / multi / true_false question. Table: {@code options}. */
@Entity
@Table(name = "options")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false, updatable = false)
    private Question question;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 150)
    private String label;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    QuestionOption(Question question, int position, String label, boolean correct) {
        this.question = question;
        this.position = position;
        this.label = label;
        this.correct = correct;
    }
}
