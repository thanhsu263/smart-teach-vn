package com.smartteach.quiz.service;

import com.smartteach.quiz.dto.OptionRequest;
import com.smartteach.quiz.dto.QuestionRequest;
import com.smartteach.quiz.dto.QuizRequest;
import com.smartteach.quiz.entity.QuestionKind;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Rules that depend on the question kind. Sizes and blank checks are done earlier by Bean
 * Validation on the DTOs. Error keys use the same paths as Bean Validation
 * ({@code questions[2].options}) so the frontend can show them next to the right input.
 */
public final class QuizRules {

    public static final Set<Integer> TIME_LIMITS = Set.of(10, 20, 30, 60, 90);

    private QuizRules() {
    }

    /** Returns an empty map when the quiz is valid. */
    public static Map<String, String> check(QuizRequest quiz) {
        Map<String, String> errors = new LinkedHashMap<>();
        List<QuestionRequest> questions = quiz.questions();
        for (int i = 0; i < questions.size(); i++) {
            checkQuestion("questions[" + i + "].", questions.get(i), errors);
        }
        return errors;
    }

    private static void checkQuestion(String path, QuestionRequest question, Map<String, String> errors) {
        if (!TIME_LIMITS.contains(question.timeLimitSeconds())) {
            errors.put(path + "timeLimitSeconds", "Must be one of 10, 20, 30, 60, 90");
        }

        if (question.kind() == QuestionKind.SHORT_TEXT) {
            if (!question.options().isEmpty()) {
                errors.put(path + "options", "Short-text questions have no options");
            }
            if (question.acceptedAnswers().isEmpty()) {
                errors.put(path + "acceptedAnswers", "Add at least one accepted answer");
            } else if (hasDuplicates(question.acceptedAnswers())) {
                errors.put(path + "acceptedAnswers", "Accepted answers must be different");
            }
            return;
        }

        if (!question.acceptedAnswers().isEmpty()) {
            errors.put(path + "acceptedAnswers", "Only short-text questions have accepted answers");
        }
        if (question.ignoreDiacritics()) {
            errors.put(path + "ignoreDiacritics", "Only short-text questions can ignore diacritics");
        }
        String optionsError = optionsError(question);
        if (optionsError != null) {
            errors.put(path + "options", optionsError);
        }
    }

    private static String optionsError(QuestionRequest question) {
        List<OptionRequest> options = question.options();
        long correct = options.stream().filter(OptionRequest::correct).count();
        String error = switch (question.kind()) {
            case SINGLE -> options.size() < 2 ? "Add at least 2 options"
                    : correct != 1 ? "Mark exactly one correct option" : null;
            case MULTI -> options.size() < 2 ? "Add at least 2 options"
                    : correct == 0 ? "Mark at least one correct option" : null;
            case TRUE_FALSE -> options.size() != 2 ? "True/false questions have exactly 2 options"
                    : correct != 1 ? "Mark exactly one correct option" : null;
            case SHORT_TEXT -> throw new IllegalStateException("handled by the caller");
        };
        if (error == null && hasDuplicates(options.stream().map(OptionRequest::label).toList())) {
            error = "Options must be different";
        }
        return error;
    }

    /** "Paris" and " paris " count as the same text. */
    private static boolean hasDuplicates(List<String> values) {
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            if (!seen.add(value.strip().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
