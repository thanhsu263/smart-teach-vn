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

/** An accepted answer of a short_text question. */
@Entity
@Table(name = "question_accepted_answers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcceptedAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false, updatable = false)
    private Question question;

    @Column(nullable = false, length = 100)
    private String value;

    AcceptedAnswer(Question question, String value) {
        this.question = question;
        this.value = value;
    }
}
