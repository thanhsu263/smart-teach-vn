package com.smartteach.quiz.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "questions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quiz_id", nullable = false, updatable = false)
    private Long quizId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 20)
    private QuestionKind kind;

    @Column(nullable = false, length = 500)
    private String prompt;

    @Column(name = "time_limit_s", nullable = false)
    private int timeLimitSeconds;

    @Column(name = "ignore_diacritics", nullable = false)
    private boolean ignoreDiacritics;

    // PERSIST only: questions are deleted in bulk and the database cascades to these rows.
    @OneToMany(mappedBy = "question", cascade = CascadeType.PERSIST)
    @OrderBy("position")
    private List<QuestionOption> options = new ArrayList<>();

    @OneToMany(mappedBy = "question", cascade = CascadeType.PERSIST)
    @OrderBy("id")
    private List<AcceptedAnswer> acceptedAnswers = new ArrayList<>();

    public Question(Long quizId, int position, QuestionKind kind, String prompt, int timeLimitSeconds,
                    boolean ignoreDiacritics) {
        this.quizId = quizId;
        this.position = position;
        this.kind = kind;
        this.prompt = prompt;
        this.timeLimitSeconds = timeLimitSeconds;
        this.ignoreDiacritics = ignoreDiacritics;
    }

    public void addOption(String label, boolean correct) {
        options.add(new QuestionOption(this, options.size(), label, correct));
    }

    public void addAcceptedAnswer(String value) {
        acceptedAnswers.add(new AcceptedAnswer(this, value));
    }
}
