package com.smartteach.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartteach.quiz.dto.OptionRequest;
import com.smartteach.quiz.dto.QuestionRequest;
import com.smartteach.quiz.dto.QuizRequest;
import com.smartteach.quiz.entity.QuestionKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QuizRulesTest {

    private static final String OPTIONS = "questions[0].options";
    private static final String ANSWERS = "questions[0].acceptedAnswers";

    @Test
    void emptyQuizIsValid() {
        assertThat(QuizRules.check(new QuizRequest("Nháp", null))).isEmpty();
    }

    @Test
    void errorsPointAtTheRightQuestion() {
        Map<String, String> errors = QuizRules.check(new QuizRequest("Quiz", List.of(
                choice(QuestionKind.SINGLE, opt("A", true), opt("B", false)),
                choice(QuestionKind.SINGLE, opt("A", false), opt("B", false)))));

        assertThat(errors).containsOnlyKeys("questions[1].options");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 5, 45, 120})
    void timeLimitMustBeOneOfTheAllowedValues(int seconds) {
        QuestionRequest question = new QuestionRequest(QuestionKind.SINGLE, "Câu hỏi", seconds, false,
                List.of(opt("A", true), opt("B", false)), null);

        assertThat(check(question)).containsOnlyKeys("questions[0].timeLimitSeconds");
    }

    @Test
    void missingTimeLimitDefaultsTo20Seconds() {
        QuestionRequest question = new QuestionRequest(QuestionKind.SINGLE, "Câu hỏi", null, false,
                List.of(opt("A", true), opt("B", false)), null);

        assertThat(question.timeLimitSeconds()).isEqualTo(20);
        assertThat(check(question)).isEmpty();
    }

    @Nested
    class Single {

        @Test
        void needsExactlyOneCorrectOption() {
            assertThat(check(choice(QuestionKind.SINGLE, opt("A", true), opt("B", false), opt("C", false)))).isEmpty();
            assertThat(check(choice(QuestionKind.SINGLE, opt("A", false), opt("B", false))))
                    .containsEntry(OPTIONS, "Mark exactly one correct option");
            assertThat(check(choice(QuestionKind.SINGLE, opt("A", true), opt("B", true))))
                    .containsEntry(OPTIONS, "Mark exactly one correct option");
        }

        @Test
        void needsAtLeastTwoOptions() {
            assertThat(check(choice(QuestionKind.SINGLE, opt("A", true))))
                    .containsEntry(OPTIONS, "Add at least 2 options");
        }

        @Test
        void optionsMustBeDifferentIgnoringCaseAndSpaces() {
            assertThat(check(choice(QuestionKind.SINGLE, opt("Hà Nội", true), opt(" hà nội ", false))))
                    .containsEntry(OPTIONS, "Options must be different");
        }

        @Test
        void cannotHaveAcceptedAnswersOrIgnoreDiacritics() {
            QuestionRequest question = new QuestionRequest(QuestionKind.SINGLE, "Câu hỏi", 20, true,
                    List.of(opt("A", true), opt("B", false)), List.of("A"));

            assertThat(check(question)).containsOnlyKeys(ANSWERS, "questions[0].ignoreDiacritics");
        }
    }

    @Nested
    class Multi {

        @Test
        void allowsSeveralCorrectOptions() {
            assertThat(check(choice(QuestionKind.MULTI, opt("A", true), opt("B", true), opt("C", false)))).isEmpty();
        }

        @Test
        void needsAtLeastOneCorrectOption() {
            assertThat(check(choice(QuestionKind.MULTI, opt("A", false), opt("B", false))))
                    .containsEntry(OPTIONS, "Mark at least one correct option");
        }

        @Test
        void needsAtLeastTwoOptions() {
            assertThat(check(choice(QuestionKind.MULTI, opt("A", true))))
                    .containsEntry(OPTIONS, "Add at least 2 options");
        }
    }

    @Nested
    class TrueFalse {

        @Test
        void needsTwoOptionsWithOneCorrect() {
            assertThat(check(choice(QuestionKind.TRUE_FALSE, opt("Đúng", false), opt("Sai", true)))).isEmpty();
            assertThat(check(choice(QuestionKind.TRUE_FALSE, opt("Đúng", true), opt("Sai", true))))
                    .containsEntry(OPTIONS, "Mark exactly one correct option");
        }

        @Test
        void rejectsAnyOtherNumberOfOptions() {
            assertThat(check(choice(QuestionKind.TRUE_FALSE, opt("Đúng", true))))
                    .containsEntry(OPTIONS, "True/false questions have exactly 2 options");
            assertThat(check(choice(QuestionKind.TRUE_FALSE, opt("Đúng", true), opt("Sai", false), opt("?", false))))
                    .containsEntry(OPTIONS, "True/false questions have exactly 2 options");
        }
    }

    @Nested
    class ShortText {

        @Test
        void needsAtLeastOneAcceptedAnswer() {
            assertThat(check(text(List.of("Hà Nội", "Ha Noi")))).isEmpty();
            assertThat(check(text(List.of())))
                    .containsEntry(ANSWERS, "Add at least one accepted answer");
        }

        @Test
        void acceptedAnswersMustBeDifferent() {
            assertThat(check(text(List.of("Hà Nội", "hà nội"))))
                    .containsEntry(ANSWERS, "Accepted answers must be different");
        }

        @Test
        void cannotHaveOptions() {
            QuestionRequest question = new QuestionRequest(QuestionKind.SHORT_TEXT, "Thủ đô?", 30, true,
                    List.of(opt("A", true)), List.of("Hà Nội"));

            assertThat(check(question)).containsOnlyKeys(OPTIONS);
        }
    }

    // --- helpers ---

    private static Map<String, String> check(QuestionRequest question) {
        return QuizRules.check(new QuizRequest("Quiz", List.of(question)));
    }

    private static QuestionRequest choice(QuestionKind kind, OptionRequest... options) {
        return new QuestionRequest(kind, "Câu hỏi", 20, false, List.of(options), null);
    }

    private static QuestionRequest text(List<String> answers) {
        return new QuestionRequest(QuestionKind.SHORT_TEXT, "Thủ đô của Việt Nam?", 30, true, null, answers);
    }

    private static OptionRequest opt(String label, boolean correct) {
        return new OptionRequest(label, correct);
    }
}
