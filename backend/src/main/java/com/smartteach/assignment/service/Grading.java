package com.smartteach.assignment.service;

import com.smartteach.quiz.dto.OptionResponse;
import com.smartteach.quiz.dto.QuestionResponse;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import tools.jackson.databind.JsonNode;

/** Pure functions: what a valid answer looks like for each question kind, and whether it is correct. */
final class Grading {

    static final int MAX_TEXT_ANSWER = 200;
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private Grading() {
    }

    /** Returns null when the answer has the right shape for the question, otherwise the reason. */
    static String checkShape(QuestionResponse question, JsonNode answer) {
        int optionCount = question.options().size();
        return switch (question.kind()) {
            case SINGLE, TRUE_FALSE -> isOptionIndex(answer, optionCount) ? null
                    : "Expected the index of one option (0 to " + (optionCount - 1) + ")";
            case MULTI -> isOptionIndexList(answer, optionCount) ? null
                    : "Expected a list of different option indexes (0 to " + (optionCount - 1) + ")";
            case SHORT_TEXT -> answer.isString() && !answer.stringValue().isBlank()
                    && answer.stringValue().length() <= MAX_TEXT_ANSWER ? null
                    : "Expected a text of 1 to " + MAX_TEXT_ANSWER + " characters";
        };
    }

    /** Expects an answer that passed {@link #checkShape}. */
    static boolean isCorrect(QuestionResponse question, JsonNode answer) {
        return switch (question.kind()) {
            case SINGLE, TRUE_FALSE -> question.options().get(answer.intValue()).correct();
            // All correct options and nothing else: no partial credit.
            case MULTI -> chosenIndexes(answer).equals(correctIndexes(question.options()));
            case SHORT_TEXT -> {
                String given = normalize(answer.stringValue(), question.ignoreDiacritics());
                yield question.acceptedAnswers().stream()
                        .anyMatch(accepted -> normalize(accepted, question.ignoreDiacritics()).equals(given));
            }
        };
    }

    /**
     * Trims, collapses spaces and ignores case. With {@code ignoreDiacritics}, "Phan Xi Păng" and
     * "phan xi pang" are equal: NFD splits "ă" into "a" + a combining mark that is then removed.
     * "đ" is a letter of its own in Unicode, not d + mark, so it is replaced explicitly.
     */
    static String normalize(String text, boolean ignoreDiacritics) {
        String result = SPACES.matcher(text.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
        if (ignoreDiacritics) {
            result = result.replace('đ', 'd');
            result = COMBINING_MARKS.matcher(Normalizer.normalize(result, Normalizer.Form.NFD)).replaceAll("");
        }
        return Normalizer.normalize(result, Normalizer.Form.NFC);
    }

    /** Percentage rounded to the nearest whole number (0-100, like the database column). */
    static int score(int correct, int total) {
        return total == 0 ? 0 : (int) Math.round(100.0 * correct / total);
    }

    private static boolean isOptionIndex(JsonNode node, int optionCount) {
        return node.isInt() && node.intValue() >= 0 && node.intValue() < optionCount;
    }

    private static boolean isOptionIndexList(JsonNode node, int optionCount) {
        if (!node.isArray() || node.isEmpty() || node.size() > optionCount) {
            return false;
        }
        Set<Integer> seen = new HashSet<>();
        for (JsonNode item : node) {
            if (!isOptionIndex(item, optionCount) || !seen.add(item.intValue())) {
                return false;
            }
        }
        return true;
    }

    private static Set<Integer> chosenIndexes(JsonNode answer) {
        Set<Integer> chosen = new HashSet<>();
        answer.forEach(item -> chosen.add(item.intValue()));
        return chosen;
    }

    private static Set<Integer> correctIndexes(List<OptionResponse> options) {
        Set<Integer> correct = new HashSet<>();
        IntStream.range(0, options.size()).filter(i -> options.get(i).correct()).forEach(correct::add);
        return correct;
    }
}
