package com.smartteach.quiz.entity;

import com.fasterxml.jackson.annotation.JsonValue;

/** Stored and sent as the lowercase value ({@code single}, {@code true_false}, ...). */
public enum QuestionKind {
    SINGLE("single"),
    TRUE_FALSE("true_false"),
    MULTI("multi"),
    SHORT_TEXT("short_text");

    private final String value;

    QuestionKind(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static QuestionKind fromValue(String value) {
        for (QuestionKind kind : values()) {
            if (kind.value.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown question kind: " + value);
    }
}
