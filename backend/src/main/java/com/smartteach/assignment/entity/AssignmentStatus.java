package com.smartteach.assignment.entity;

import com.fasterxml.jackson.annotation.JsonValue;

/** draft -> published -> closed. Stored and sent in lowercase. */
public enum AssignmentStatus {
    DRAFT("draft"),
    PUBLISHED("published"),
    CLOSED("closed");

    private final String value;

    AssignmentStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static AssignmentStatus fromValue(String value) {
        for (AssignmentStatus item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Unknown AssignmentStatus: " + value);
    }
}
