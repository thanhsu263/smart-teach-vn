package com.smartteach.common.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;

/**
 * Validation that needs more than one field (e.g. "exactly one correct option"). Rendered like
 * Bean Validation errors: 400 with an {@code errors} map of field path -> message.
 */
@Getter
public class ValidationFailedException extends RuntimeException {

    private final Map<String, String> errors;

    public ValidationFailedException(Map<String, String> errors) {
        super("Validation failed");
        this.errors = Collections.unmodifiableMap(new LinkedHashMap<>(errors)); // keeps question order
    }
}
