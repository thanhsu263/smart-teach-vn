package com.smartteach.live.service;

import com.fasterxml.jackson.annotation.JsonValue;

/** lobby → question → reveal → (next question … ) → ended. Sent as the lowercase value. */
public enum LiveStatus {
    LOBBY, QUESTION, REVEAL, ENDED;

    @JsonValue
    public String getValue() {
        return name().toLowerCase();
    }
}
