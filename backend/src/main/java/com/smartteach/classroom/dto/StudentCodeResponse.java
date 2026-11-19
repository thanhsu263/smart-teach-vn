package com.smartteach.classroom.dto;

/** Returned only when a code is created; the plain code cannot be read again later. */
public record StudentCodeResponse(Long id, String name, int position, String accessCode, String codeHint) {
}
