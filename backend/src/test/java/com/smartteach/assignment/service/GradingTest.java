package com.smartteach.assignment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartteach.quiz.dto.OptionResponse;
import com.smartteach.quiz.dto.QuestionResponse;
import com.smartteach.quiz.entity.QuestionKind;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class GradingTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final QuestionResponse SINGLE = choice(QuestionKind.SINGLE, true, false, false);
    private static final QuestionResponse TRUE_FALSE = choice(QuestionKind.TRUE_FALSE, false, true);
    private static final QuestionResponse MULTI = choice(QuestionKind.MULTI, true, false, true, false);
    private static final QuestionResponse TEXT_ACCENTS = text(false, "Hồ Chí Minh");
    private static final QuestionResponse TEXT_NO_ACCENTS = text(true, "Đà Lạt", "Phan Xi Păng");

    @Test
    void singleAndTrueFalseNeedOneValidIndex() {
        assertThat(Grading.checkShape(SINGLE, json("2"))).isNull();
        assertThat(Grading.checkShape(SINGLE, json("3"))).contains("0 to 2");
        assertThat(Grading.checkShape(SINGLE, json("-1"))).isNotNull();
        assertThat(Grading.checkShape(SINGLE, json("\"0\""))).isNotNull();
        assertThat(Grading.checkShape(SINGLE, json("1.0"))).isNotNull();
        assertThat(Grading.checkShape(TRUE_FALSE, json("[1]"))).isNotNull();

        assertThat(Grading.isCorrect(SINGLE, json("0"))).isTrue();
        assertThat(Grading.isCorrect(SINGLE, json("1"))).isFalse();
        assertThat(Grading.isCorrect(TRUE_FALSE, json("1"))).isTrue();
    }

    @Test
    void multiNeedsDistinctIndexesAndExactlyTheCorrectSet() {
        assertThat(Grading.checkShape(MULTI, json("[2, 0]"))).isNull();
        assertThat(Grading.checkShape(MULTI, json("[]"))).isNotNull();
        assertThat(Grading.checkShape(MULTI, json("[0, 0]"))).isNotNull();
        assertThat(Grading.checkShape(MULTI, json("[0, 4]"))).isNotNull();
        assertThat(Grading.checkShape(MULTI, json("[0, 1, 2, 3, 0]"))).isNotNull();
        assertThat(Grading.checkShape(MULTI, json("0"))).isNotNull();

        assertThat(Grading.isCorrect(MULTI, json("[2, 0]"))).isTrue();
        assertThat(Grading.isCorrect(MULTI, json("[0]"))).isFalse();         // missing one
        assertThat(Grading.isCorrect(MULTI, json("[0, 1, 2]"))).isFalse();   // one too many
    }

    @Test
    void shortTextNeedsANonBlankStringOfAtMost200Characters() {
        assertThat(Grading.checkShape(TEXT_ACCENTS, json("\"Huế\""))).isNull();
        assertThat(Grading.checkShape(TEXT_ACCENTS, json("\"   \""))).isNotNull();
        assertThat(Grading.checkShape(TEXT_ACCENTS, json("12"))).isNotNull();
        assertThat(Grading.checkShape(TEXT_ACCENTS, JSON.getNodeFactory().stringNode("x".repeat(201)))).isNotNull();
    }

    @Test
    void shortTextIgnoresCaseAndSpacesButKeepsAccentsUnlessAsked() {
        assertThat(Grading.isCorrect(TEXT_ACCENTS, json("\"  hồ   CHÍ minh \""))).isTrue();
        assertThat(Grading.isCorrect(TEXT_ACCENTS, json("\"Ho Chi Minh\""))).isFalse();

        assertThat(Grading.isCorrect(TEXT_NO_ACCENTS, json("\"da lat\""))).isTrue();
        assertThat(Grading.isCorrect(TEXT_NO_ACCENTS, json("\"ĐÀ LẠT\""))).isTrue();
        assertThat(Grading.isCorrect(TEXT_NO_ACCENTS, json("\"phan xi pang\""))).isTrue();
        assertThat(Grading.isCorrect(TEXT_NO_ACCENTS, json("\"Sa Pa\""))).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "'  Phan  Xi Păng ', false, phan xi păng",
            "Phan Xi Păng, true, phan xi pang",
            "Đường Đắk Lắk, true, duong dak lak",
            "Nguyễn Thị Ánh, true, nguyen thi anh",
    })
    void normalize(String input, boolean ignoreDiacritics, String expected) {
        assertThat(Grading.normalize(input, ignoreDiacritics)).isEqualTo(expected);
    }

    @Test
    void normalizeTreatsComposedAndDecomposedAccentsAlike() {
        String composed = "Huế";                 // "ế" as one code point
        String decomposed = "Huế";   // e + circumflex + acute
        assertThat(Grading.normalize(decomposed, false)).isEqualTo(Grading.normalize(composed, false));
    }

    @ParameterizedTest
    @CsvSource({"0, 4, 0", "1, 4, 25", "1, 3, 33", "2, 3, 67", "4, 4, 100", "0, 0, 0"})
    void scoreIsARoundedPercentage(int correct, int total, int expected) {
        assertThat(Grading.score(correct, total)).isEqualTo(expected);
    }

    private static JsonNode json(String value) {
        return JSON.readTree(value);
    }

    private static QuestionResponse choice(QuestionKind kind, boolean... correct) {
        List<OptionResponse> options = new java.util.ArrayList<>();
        for (int i = 0; i < correct.length; i++) {
            options.add(new OptionResponse("Option " + i, correct[i]));
        }
        return new QuestionResponse(0, kind, "?", 20, false, options, List.of());
    }

    private static QuestionResponse text(boolean ignoreDiacritics, String... accepted) {
        return new QuestionResponse(0, QuestionKind.SHORT_TEXT, "?", 20, ignoreDiacritics, List.of(), List.of(accepted));
    }
}
