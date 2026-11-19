package com.smartteach.assignment.entity;

import com.fasterxml.jackson.annotation.JsonValue;

/** When a student may see their score and the correct answers. */
public enum RevealPolicy {
    IMMEDIATE("immediate"),
    AFTER_CLOSE("after_close");

    private final String value;

    RevealPolicy(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static RevealPolicy fromValue(String value) {
        for (RevealPolicy item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Unknown RevealPolicy: " + value);
    }
}
