package com.smartteach.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.smartteach.IntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class QuizIntegrationTest extends IntegrationTest {

    /** One question of each kind. */
    private static final String FULL_QUIZ = """
            {"title": "  Địa lý 10  ", "questions": [
              {"kind": "single", "prompt": "Thủ đô của Việt Nam?", "timeLimitSeconds": 30,
               "options": [{"label": "Hà Nội", "correct": true}, {"label": "Huế", "correct": false}]},
              {"kind": "true_false", "prompt": "Sông Mê Kông chảy qua Việt Nam.",
               "options": [{"label": "Đúng", "correct": true}, {"label": "Sai", "correct": false}]},
              {"kind": "multi", "prompt": "Tỉnh nào giáp biển?", "timeLimitSeconds": 60,
               "options": [{"label": "Đà Nẵng", "correct": true}, {"label": "Gia Lai", "correct": false},
                           {"label": "Khánh Hoà", "correct": true}]},
              {"kind": "short_text", "prompt": "Đỉnh núi cao nhất Việt Nam?", "ignoreDiacritics": true,
               "acceptedAnswers": ["Phan Xi Păng", "Fansipan"]}
            ]}""";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void teacherCanCreateReadUpdateAndDeleteQuiz() throws Exception {
        String teacher = signUpTeacher();
        long id = createQuiz(teacher, FULL_QUIZ);

        perform(teacher, get("/api/quizzes/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Địa lý 10"))
                .andExpect(jsonPath("$.questions[*].kind").value(contains("single", "true_false", "multi", "short_text")))
                .andExpect(jsonPath("$.questions[*].position").value(contains(0, 1, 2, 3)))
                .andExpect(jsonPath("$.questions[0].timeLimitSeconds").value(30))
                .andExpect(jsonPath("$.questions[1].timeLimitSeconds").value(20))
                .andExpect(jsonPath("$.questions[2].options[*].label").value(contains("Đà Nẵng", "Gia Lai", "Khánh Hoà")))
                .andExpect(jsonPath("$.questions[2].options[*].correct").value(contains(true, false, true)))
                .andExpect(jsonPath("$.questions[3].options", hasSize(0)))
                .andExpect(jsonPath("$.questions[3].ignoreDiacritics").value(true))
                .andExpect(jsonPath("$.questions[3].acceptedAnswers").value(contains("Phan Xi Păng", "Fansipan")));

        perform(teacher, get("/api/quizzes"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].questionCount").value(4));

        // Saving replaces every question: here the old last question becomes the only one.
        perform(teacher, put("/api/quizzes/" + id), """
                {"title": "Địa lý 10 – sửa", "questions": [
                  {"kind": "short_text", "prompt": "Đỉnh núi cao nhất?", "acceptedAnswers": ["Fansipan"]}]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions", hasSize(1)))
                .andExpect(jsonPath("$.questions[0].position").value(0));
        perform(teacher, get("/api/quizzes/" + id))
                .andExpect(jsonPath("$.title").value("Địa lý 10 – sửa"))
                .andExpect(jsonPath("$.questions", hasSize(1)))
                .andExpect(jsonPath("$.questions[0].acceptedAnswers").value(contains("Fansipan")));
        assertThat(countRows("options", id)).isZero();

        perform(teacher, delete("/api/quizzes/" + id)).andExpect(status().isNoContent());
        perform(teacher, get("/api/quizzes/" + id)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questions WHERE quiz_id = ?", Integer.class, id)).isZero();
    }

    @Test
    void duplicateCreatesAnIndependentCopy() throws Exception {
        String teacher = signUpTeacher();
        long id = createQuiz(teacher, FULL_QUIZ);

        String json = perform(teacher, post("/api/quizzes/" + id + "/duplicate"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Địa lý 10 (bản sao)"))
                .andExpect(jsonPath("$.questions", hasSize(4)))
                .andExpect(jsonPath("$.questions[3].acceptedAnswers").value(contains("Phan Xi Păng", "Fansipan")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long copyId = ((Number) JsonPath.read(json, "$.id")).longValue();

        perform(teacher, delete("/api/quizzes/" + id)).andExpect(status().isNoContent());
        perform(teacher, get("/api/quizzes/" + copyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[2].options", hasSize(3)));
    }

    @Test
    void anotherTeachersQuizIsNotFound() throws Exception {
        String owner = signUpTeacher();
        long id = createQuiz(owner, FULL_QUIZ);

        String other = signUpTeacher();
        perform(other, get("/api/quizzes")).andExpect(jsonPath("$.totalElements").value(0));
        perform(other, get("/api/quizzes/" + id)).andExpect(status().isNotFound());
        perform(other, put("/api/quizzes/" + id), "{\"title\":\"Của tôi\",\"questions\":[]}")
                .andExpect(status().isNotFound());
        perform(other, post("/api/quizzes/" + id + "/duplicate")).andExpect(status().isNotFound());
        perform(other, delete("/api/quizzes/" + id)).andExpect(status().isNotFound());

        perform(owner, get("/api/quizzes/" + id))
                .andExpect(jsonPath("$.title").value("Địa lý 10"))
                .andExpect(jsonPath("$.questions", hasSize(4)));
    }

    @Test
    void invalidQuizReturnsErrorsPerQuestion() throws Exception {
        String teacher = signUpTeacher();

        // Rule checked by QuizRules (depends on the kind).
        perform(teacher, post("/api/quizzes"), """
                {"title": "Quiz", "questions": [
                  {"kind": "single", "prompt": "A?", "options": [{"label": "A", "correct": true}, {"label": "B"}]},
                  {"kind": "single", "prompt": "B?", "options": [{"label": "A"}, {"label": "B"}]}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['questions[1].options']").value("Mark exactly one correct option"))
                .andExpect(jsonPath("$.errors['questions[0].options']").doesNotExist());

        // Rules checked by Bean Validation.
        perform(teacher, post("/api/quizzes"), """
                {"title": " ", "questions": [{"kind": "multi", "prompt": "",
                  "options": [{"label": "A", "correct": true}, {"label": "%s"}]}]}""".formatted("x".repeat(151)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors['questions[0].prompt']").exists())
                .andExpect(jsonPath("$.errors['questions[0].options[1].label']").exists());

        perform(teacher, post("/api/quizzes"), "{\"title\":\"Quiz\",\"questions\":[{\"kind\":\"essay\",\"prompt\":\"?\"}]}")
                .andExpect(status().isBadRequest());

        String tooMany = Collections.nCopies(51, """
                {"kind": "short_text", "prompt": "?", "acceptedAnswers": ["a"]}""")
                .stream().collect(Collectors.joining(","));
        perform(teacher, post("/api/quizzes"), "{\"title\":\"Quiz\",\"questions\":[" + tooMany + "]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.questions").value("A quiz can have at most 50 questions"));

        perform(teacher, get("/api/quizzes")).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void deletingAQuizKeepsAssignmentsMadeFromIt() throws Exception {
        String teacher = signUpTeacher();
        long quizId = createQuiz(teacher, FULL_QUIZ);
        long assignmentId = insertAssignment(teacher, quizId);

        perform(teacher, delete("/api/quizzes/" + quizId)).andExpect(status().isNoContent());

        // jsonb is stored normalised, so compare as jsonb rather than as text.
        var row = jdbc.queryForMap("""
                SELECT quiz_id, quiz_snapshot = '{"questions":[]}'::jsonb AS snapshot_kept
                FROM assignments WHERE id = ?""", assignmentId);
        assertThat(row.get("quiz_id")).isNull();
        assertThat(row.get("snapshot_kept")).isEqualTo(true);
    }

    @Test
    void readingAQuizTakesThreeQueriesWhateverItsSize() throws Exception {
        String teacher = signUpTeacher();
        String question = """
                {"kind": "multi", "prompt": "?", "options": [{"label": "A", "correct": true}, {"label": "B"}]}""";
        String text = """
                {"kind": "short_text", "prompt": "?", "acceptedAnswers": ["a", "b"]}""";
        String questions = IntStream.range(0, 20).mapToObj(i -> i % 2 == 0 ? question : text)
                .collect(Collectors.joining(","));
        long id = createQuiz(teacher, "{\"title\":\"Lớn\",\"questions\":[" + questions + "]}");

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        perform(teacher, get("/api/quizzes/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions", hasSize(20)))
                .andExpect(jsonPath("$.questions[18].options", hasSize(2)))
                .andExpect(jsonPath("$.questions[19].acceptedAnswers", hasSize(2)));

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
    }

    @Test
    void aTeacherCanHaveAtMost50Quizzes() throws Exception {
        String teacher = signUpTeacher();
        long ownerId = ((Number) JsonPath.read(perform(teacher, get("/api/me"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id")).longValue();
        for (int i = 0; i < 49; i++) {
            jdbc.update("INSERT INTO quizzes (owner_id, title) VALUES (?, ?)", ownerId, "Quiz " + i);
        }

        long last = createQuiz(teacher, "{\"title\":\"Quiz 50\"}");
        perform(teacher, post("/api/quizzes"), "{\"title\":\"Quiz 51\"}").andExpect(status().isConflict());
        perform(teacher, post("/api/quizzes/" + last + "/duplicate")).andExpect(status().isConflict());
    }

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/quizzes")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/quizzes/1/duplicate")).andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private long createQuiz(String auth, String json) throws Exception {
        String response = perform(auth, post("/api/quizzes"), json)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private int countRows(String table, long quizId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " o JOIN questions q ON q.id = o.question_id "
                + "WHERE q.quiz_id = ?", Integer.class, quizId);
    }

    // Assignments have no API yet (Phase 4), so one is inserted directly.
    private long insertAssignment(String auth, long quizId) throws Exception {
        String json = perform(auth, post("/api/classes"), "{\"name\":\"10A1\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long classId = ((Number) JsonPath.read(json, "$.id")).longValue();
        Long ownerId = jdbc.queryForObject("SELECT owner_id FROM quizzes WHERE id = ?", Long.class, quizId);
        String code = String.format("%010X", System.nanoTime() & 0xFFFFFFFFFFL);
        jdbc.update("""
                INSERT INTO assignments (owner_id, class_id, quiz_id, title, public_code, status, quiz_snapshot)
                VALUES (?, ?, ?, 'Bài 1', ?, 'published', '{"questions":[]}')""", ownerId, classId, quizId, code);
        return jdbc.queryForObject("SELECT id FROM assignments WHERE public_code = ?", Long.class, code);
    }
}
