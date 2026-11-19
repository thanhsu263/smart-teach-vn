package com.smartteach.assignment.repository;

/** One row of {@link AttemptAnswerRepository#countByPosition}. */
public record PositionStats(int position, long answered, long correct) {
}
