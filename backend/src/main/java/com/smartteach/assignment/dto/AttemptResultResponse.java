package com.smartteach.assignment.dto;

import com.smartteach.assignment.entity.AttemptStatus;
import com.smartteach.quiz.dto.OptionResponse;
import com.smartteach.quiz.entity.QuestionKind;
import java.time.Instant;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * When {@code revealed} is false (reveal policy after_close and the assignment is not over yet), the
 * score fields are null and {@code questions} is empty.
 */
public record AttemptResultResponse(Long attemptId, String assignmentTitle, String studentName, AttemptStatus status,
                                    Instant submittedAt, boolean revealed, Integer score, Integer correctCount,
                                    Integer questionTotal, List<QuestionResult> questions) {

    public record QuestionResult(int position, QuestionKind kind, String prompt, List<OptionResponse> options,
                                 List<String> acceptedAnswers, JsonNode answer, boolean correct) {
    }
}
