package com.smartteach.assignment.entity;

import com.fasterxml.jackson.annotation.JsonValue;

/** An attempt is invalidated when the student's access code is replaced. */
public enum AttemptStatus {
    IN_PROGRESS("in_progress"),
    SUBMITTED("submitted"),
    INVALIDATED("invalidated");

    private final String value;

    AttemptStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static AttemptStatus fromValue(String value) {
        for (AttemptStatus item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Unknown AttemptStatus: " + value);
    }
}
