package com.smartteach.assignment.dto;

/** {@code token} goes in the X-Attempt-Token header of the following calls. */
public record StartAttemptResponse(String token, AttemptView attempt) {
}
