package com.smartteach.assignment.dto;

import tools.jackson.databind.JsonNode;

/**
 * An option index ({@code 1}) for single / true_false, a list of indexes ({@code [0, 2]}) for multi,
 * a string for short_text. {@code null} clears the saved answer.
 */
public record AnswerRequest(JsonNode answer) {
}
