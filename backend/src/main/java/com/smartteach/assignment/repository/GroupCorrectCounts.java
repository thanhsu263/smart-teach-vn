package com.smartteach.assignment.repository;

/** One row of {@link AttemptAnswerRepository#countCorrectByScoreGroup} (a native query, hence an interface). */
public interface GroupCorrectCounts {

    int getPosition();

    long getCorrect();

    long getUpperCorrect();

    long getLowerCorrect();
}
