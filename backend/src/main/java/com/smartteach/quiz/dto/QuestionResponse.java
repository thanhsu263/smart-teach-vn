package com.smartteach.quiz.dto;

import com.smartteach.quiz.entity.QuestionKind;
import java.util.List;

public record QuestionResponse(int position, QuestionKind kind, String prompt, int timeLimitSeconds,
                               boolean ignoreDiacritics, List<OptionResponse> options,
                               List<String> acceptedAnswers) {
}
