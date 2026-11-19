package com.smartteach.classroom.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A student in a class. Students have no account; they sign in to assignments with an access code. */
@Entity
@Table(name = "students")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false, updatable = false)
    private Classroom classroom;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int position;

    /** SHA-256 of the access code. The code itself is shown to the teacher once and never stored. */
    @Column(name = "access_code_hash", nullable = false, length = 100)
    private String accessCodeHash;

    /** Last 4 characters of the code, so the teacher can tell codes apart without seeing them. */
    @Column(name = "code_hint", nullable = false, length = 4)
    private String codeHint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Student(Classroom classroom, String name, int position, Instant createdAt) {
        this.classroom = classroom;
        this.name = name;
        this.position = position;
        this.createdAt = createdAt;
    }
}
