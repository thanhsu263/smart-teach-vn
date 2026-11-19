package com.smartteach.assignment.dto;

import com.smartteach.assignment.entity.AttemptStatus;
import com.smartteach.assignment.entity.RevealPolicy;
import com.smartteach.quiz.entity.QuestionKind;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/** What a student sees while answering: the questions without the correct answers, and their saved answers. */
public record AttemptView(Long attemptId, String assignmentTitle, String description, String studentName,
                          AttemptStatus status, boolean open, Instant dueAt, RevealPolicy revealPolicy,
                          List<PublicQuestion> questions, Map<Integer, JsonNode> answers) {

    public record PublicQuestion(int position, QuestionKind kind, String prompt, List<String> options) {
    }
}
