package com.smartteach.assignment;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.smartteach.IntegrationTest;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Item analysis on a dataset small enough to compute by hand. Quiz (correct answer in brackets):
 * Q0 single "Hà Nội"/"Huế" [0], Q1 true/false "Đúng"/"Sai" [0], Q2 multi "Đà Nẵng"/"Gia Lai"/
 * "Khánh Hoà" [0, 2], Q3 short text [Phan Xi Păng / Fansipan, accents ignored].
 */
class QuestionStatsIntegrationTest extends IntegrationTest {

    private static final String QUIZ = """
            {"title": "Địa lý 10", "questions": [
              {"kind": "single", "prompt": "Thủ đô của Việt Nam?",
               "options": [{"label": "Hà Nội", "correct": true}, {"label": "Huế", "correct": false}]},
              {"kind": "true_false", "prompt": "Sông Mê Kông chảy qua Việt Nam.",
               "options": [{"label": "Đúng", "correct": true}, {"label": "Sai", "correct": false}]},
              {"kind": "multi", "prompt": "Tỉnh nào giáp biển?",
               "options": [{"label": "Đà Nẵng", "correct": true}, {"label": "Gia Lai", "correct": false},
                           {"label": "Khánh Hoà", "correct": true}]},
              {"kind": "short_text", "prompt": "Đỉnh núi cao nhất Việt Nam?", "ignoreDiacritics": true,
               "acceptedAnswers": ["Phan Xi Păng", "Fansipan"]}
            ]}""";

    /**
     * Answers of S0..S10 to Q0..Q3 (null = left empty), in the order they start, so attempt ids
     * grow with the row number. Score = correct answers x 25.
     */
    private static final String[][] ANSWERS = {
            {"0", "0", "[2, 0]", "\"Phan Xi Păng\""},  // S0  100
            {"0", "0", "[0, 2]", "\"fansipan\""},      // S1  100
            {"0", "1", "[0, 2]", "\"phan xi pang\""},  // S2   75
            {"0", "0", "[0, 2]", "\"Bạch Mã\""},       // S3   75
            {"0", "0", "[0]", "\"bach ma \""},         // S4   50
            {"0", "1", "[2, 1]", null},                // S5   25
            {"1", "0", "[1, 2]", null},                // S6   25
            {"0", "0", "[0]", "\"Ngọc Linh\""},        // S7   50
            {"1", "0", "[2, 1]", "\"  BACH  MA\""},    // S8   25
            {"1", "1", "[1, 2]", "\"Ngọc Linh\""},     // S9    0
            {"1", "0", null, null},                    // S10  25
    };

    @Test
    void computesDifficultyDiscriminationAndMostCommonWrongAnswer() throws Exception {
        Setup s = setUp(ANSWERS.length + 1);
        for (int i = 0; i < ANSWERS.length; i++) {
            Started attempt = start(s.publicCode, s.codes.get(i));
            for (int q = 0; q < 4; q++) {
                if (ANSWERS[i][q] != null) {
                    save(attempt, q, ANSWERS[i][q]).andExpect(status().isOk());
                }
            }
            submit(attempt);
        }
        // Still in progress: must not count anywhere.
        save(start(s.publicCode, s.codes.get(ANSWERS.length)), 0, "1").andExpect(status().isOk());

        // 11 submitted -> groups of round(11 x 0.27) = 3. Ranked by score, ties by attempt id:
        // S0 S1 S2 | S3 S4 S7 S5 S6 | S8 S10 S9 (S2 beats S3 and S6 stays out of the bottom on the id).
        perform(s.teacher, get("/api/assignments/" + s.assignmentId + "/question-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submittedCount").value(11))
                .andExpect(jsonPath("$.minSubmissions").value(10))
                .andExpect(jsonPath("$.groupSize").value(3))
                // Q0: 7/11 right; top 3/3, bottom 0/3 -> D = 1. "Huế" chosen 4 times.
                .andExpect(jsonPath("$.questions[0].kind").value("single"))
                .andExpect(jsonPath("$.questions[0].correct").value(7))
                .andExpect(jsonPath("$.questions[0].correctRate").value(63.6))
                .andExpect(jsonPath("$.questions[0].discrimination").value(1.0))
                .andExpect(jsonPath("$.questions[0].mostCommonWrongAnswer.answer").value("Huế"))
                .andExpect(jsonPath("$.questions[0].mostCommonWrongAnswer.count").value(4))
                .andExpect(jsonPath("$.questions[0].tooHard").value(false))
                .andExpect(jsonPath("$.questions[0].lowDiscrimination").value(false))
                // Q1: 8/11 right; top 2/3 (S2 wrong), bottom 2/3 -> D = 0: does not separate.
                .andExpect(jsonPath("$.questions[1].correct").value(8))
                .andExpect(jsonPath("$.questions[1].correctRate").value(72.7))
                .andExpect(jsonPath("$.questions[1].discrimination").value(0.0))
                .andExpect(jsonPath("$.questions[1].mostCommonWrongAnswer.answer").value("Sai"))
                .andExpect(jsonPath("$.questions[1].mostCommonWrongAnswer.count").value(3))
                .andExpect(jsonPath("$.questions[1].lowDiscrimination").value(true))
                // Q2: 4/11 right. [2,1] and [1,2] are one choice (4 times) and beat [0] (2 times).
                .andExpect(jsonPath("$.questions[2].correct").value(4))
                .andExpect(jsonPath("$.questions[2].correctRate").value(36.4))
                .andExpect(jsonPath("$.questions[2].discrimination").value(1.0))
                .andExpect(jsonPath("$.questions[2].mostCommonWrongAnswer.answer").value("Gia Lai, Khánh Hoà"))
                .andExpect(jsonPath("$.questions[2].mostCommonWrongAnswer.count").value(4))
                .andExpect(jsonPath("$.questions[2].tooHard").value(false))
                // Q3: 3/11 = 27.3% -> too hard. "Bạch Mã", "bach ma " and "  BACH  MA" are one answer
                // (3) and beat "Ngọc Linh" (2); it is shown as first typed.
                .andExpect(jsonPath("$.questions[3].correct").value(3))
                .andExpect(jsonPath("$.questions[3].correctRate").value(27.3))
                .andExpect(jsonPath("$.questions[3].discrimination").value(1.0))
                .andExpect(jsonPath("$.questions[3].mostCommonWrongAnswer.answer").value("Bạch Mã"))
                .andExpect(jsonPath("$.questions[3].mostCommonWrongAnswer.count").value(3))
                .andExpect(jsonPath("$.questions[3].tooHard").value(true))
                .andExpect(jsonPath("$.questions[3].lowDiscrimination").value(false));
    }

    @Test
    void doesNotComputeDiscriminationBelowTenSubmissions() throws Exception {
        Setup s = setUp(9);
        for (int i = 0; i < 9; i++) {
            Started attempt = start(s.publicCode, s.codes.get(i));
            save(attempt, 0, i < 6 ? "0" : "1").andExpect(status().isOk());
            submit(attempt);
        }

        perform(s.teacher, get("/api/assignments/" + s.assignmentId + "/question-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submittedCount").value(9))
                .andExpect(jsonPath("$.groupSize").value(0))
                .andExpect(jsonPath("$.questions[0].correctRate").value(66.7))
                .andExpect(jsonPath("$.questions[0].discrimination").value(nullValue()))
                .andExpect(jsonPath("$.questions[0].lowDiscrimination").value(false))
                .andExpect(jsonPath("$.questions[0].mostCommonWrongAnswer.answer").value("Huế"))
                .andExpect(jsonPath("$.questions[0].mostCommonWrongAnswer.count").value(3))
                // Nobody answered Q1: 0% right, no wrong answer to show.
                .andExpect(jsonPath("$.questions[1].correctRate").value(0.0))
                .andExpect(jsonPath("$.questions[1].tooHard").value(true))
                .andExpect(jsonPath("$.questions[1].mostCommonWrongAnswer").value(nullValue()));
    }

    @Test
    void isOnlyForTheOwnerAndEmptyForADraft() throws Exception {
        String teacher = signUpTeacher();
        long draftId = createAssignment(teacher, createClass(teacher, 1).id, createQuiz(teacher));

        perform(teacher, get("/api/assignments/" + draftId + "/question-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submittedCount").value(0))
                .andExpect(jsonPath("$.questions").isEmpty());
        perform(signUpTeacher(), get("/api/assignments/" + draftId + "/question-stats"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/assignments/" + draftId + "/question-stats")).andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private record Setup(String teacher, long assignmentId, String publicCode, List<String> codes) {
    }

    private record Started(long id, String token) {
    }

    private record NewClass(long id, List<String> codes) {
    }

    /** A teacher, a class of {@code students} students, the quiz and a published assignment. */
    private Setup setUp(int students) throws Exception {
        String teacher = signUpTeacher();
        NewClass newClass = createClass(teacher, students);
        long assignmentId = createAssignment(teacher, newClass.id, createQuiz(teacher));
        String published = perform(teacher, post("/api/assignments/" + assignmentId + "/publish"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Setup(teacher, assignmentId, JsonPath.read(published, "$.publicCode"), newClass.codes);
    }

    private NewClass createClass(String teacher, int students) throws Exception {
        String classJson = perform(teacher, post("/api/classes"), "{\"name\": \"10A1\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long classId = ((Number) JsonPath.read(classJson, "$.id")).longValue();
        String names = IntStream.range(0, students).mapToObj(i -> "\"Học sinh " + i + "\"")
                .collect(Collectors.joining(", "));
        String studentsJson = perform(teacher, post("/api/classes/" + classId + "/students"),
                "{\"names\": [" + names + "]}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new NewClass(classId, JsonPath.read(studentsJson, "$[*].accessCode"));
    }

    private long createQuiz(String teacher) throws Exception {
        String json = perform(teacher, post("/api/quizzes"), QUIZ)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private long createAssignment(String teacher, long classId, long quizId) throws Exception {
        String json = perform(teacher, post("/api/assignments"), """
                {"classId": %d, "quizId": %d, "title": "Kiểm tra 15 phút", "revealPolicy": "immediate"}"""
                .formatted(classId, quizId))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private Started start(String publicCode, String accessCode) throws Exception {
        String json = mvc.perform(post("/api/public/assignments/" + publicCode + "/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessCode\": \"" + accessCode + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Started(((Number) JsonPath.read(json, "$.attempt.attemptId")).longValue(),
                JsonPath.read(json, "$.token"));
    }

    private ResultActions save(Started attempt, int position, String answerJson) throws Exception {
        return mvc.perform(put("/api/public/attempts/" + attempt.id + "/answers/" + position)
                .header("X-Attempt-Token", attempt.token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answer\": " + answerJson + "}"));
    }

    private void submit(Started attempt) throws Exception {
        mvc.perform(post("/api/public/attempts/" + attempt.id + "/submit").header("X-Attempt-Token", attempt.token))
                .andExpect(status().isOk());
    }
}
