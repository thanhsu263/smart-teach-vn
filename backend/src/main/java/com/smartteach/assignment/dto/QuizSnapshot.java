package com.smartteach.assignment.dto;

import com.smartteach.quiz.dto.QuestionResponse;
import java.util.List;

/** The quiz as it was when the assignment was published; stored as JSON in assignments.quiz_snapshot. */
public record QuizSnapshot(String title, List<QuestionResponse> questions) {
}
