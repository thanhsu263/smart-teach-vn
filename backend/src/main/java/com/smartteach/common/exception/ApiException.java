package com.smartteach.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Business error that maps directly to an HTTP status and a ProblemDetail response. */
@Getter
public class ApiException extends RuntimeException {

    /** The frontend shows its own "edited in another tab, reload" message for this 409. */
    public static final String MODIFIED_ELSEWHERE = "Modified elsewhere, reload and try again";

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    /**
     * Optimistic locking: refuses a save based on an older version. Null means the client did not
     * send one (scripts, old clients) and the save simply overwrites.
     */
    public static void checkVersion(Long expected, long actual) {
        if (expected != null && expected != actual) {
            throw conflict(MODIFIED_ELSEWHERE);
        }
    }
}
