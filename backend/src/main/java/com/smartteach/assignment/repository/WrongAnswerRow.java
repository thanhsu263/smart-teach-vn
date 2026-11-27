package com.smartteach.assignment.repository;

/** One row of {@link AttemptAnswerRepository#findWrongAnswers}: the answer as saved JSON text. */
public record WrongAnswerRow(int position, String answer) {
}
