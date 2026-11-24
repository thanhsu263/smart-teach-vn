package com.smartteach.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.smartteach.IntegrationTest;
import com.smartteach.assignment.service.AttemptRetentionJob;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Assign -> students answer with their codes -> submit and grade -> analytics, on a real PostgreSQL. */
class AssignmentFlowIntegrationTest extends IntegrationTest {

    /** Correct answers: 0, 0, [0, 2], "Phan Xi Păng" (accents ignored). */
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

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Autowired
    AttemptRetentionJob retentionJob;

    @Test
    void fullFlowFromPublishToAnalytics() throws Exception {
        Setup s = setUp("immediate");
        String code = publish(s);

        // Student 1: everything right, short text typed without accents and in lowercase.
        Started an = start(code, s.codes.get(0));
        save(an, 0, "0").andExpect(status().isOk()).andExpect(jsonPath("$.savedAt").exists());
        save(an, 1, "0").andExpect(status().isOk());
        save(an, 2, "[2, 0]").andExpect(status().isOk());
        save(an, 3, "\"  phan xi pang \"").andExpect(status().isOk());
        // Autosave overwrites: change question 0 and back.
        save(an, 0, "1").andExpect(status().isOk());
        save(an, 0, "0").andExpect(status().isOk());

        // Student 2: q0 wrong, q1 right, q2 only one of the two correct options, q3 left empty.
        Started binh = start(code, s.codes.get(1));
        save(binh, 0, "1").andExpect(status().isOk());
        save(binh, 1, "0").andExpect(status().isOk());
        save(binh, 2, "[0]").andExpect(status().isOk());
        save(binh, 3, "\"x\"").andExpect(status().isOk());
        save(binh, 3, "null").andExpect(status().isOk()).andExpect(jsonPath("$.savedAt").doesNotExist());

        // Coming back (e.g. another device) continues the same attempt with the saved answers.
        Started again = start(code, s.codes.get(0));
        assertThat(again.id).isEqualTo(an.id);
        attempt(get("/api/public/attempts/" + an.id), again.token)
                .andExpect(jsonPath("$.answers['0']").value(0))
                .andExpect(jsonPath("$.answers['2']", contains(2, 0)))
                .andExpect(jsonPath("$.answers['3']").value("  phan xi pang "));

        attempt(post("/api/public/attempts/" + an.id + "/submit"), again.token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("submitted"))
                .andExpect(jsonPath("$.revealed").value(true))
                .andExpect(jsonPath("$.score").value(100))
                .andExpect(jsonPath("$.correctCount").value(4))
                .andExpect(jsonPath("$.questions[*].correct", contains(true, true, true, true)))
                .andExpect(jsonPath("$.questions[3].acceptedAnswers", contains("Phan Xi Păng", "Fansipan")));
        // Submitting twice is harmless; answering after submit is not allowed.
        attempt(post("/api/public/attempts/" + an.id + "/submit"), again.token)
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(100));
        save(again, 0, "1").andExpect(status().isConflict());

        attempt(post("/api/public/attempts/" + binh.id + "/submit"), binh.token)
                .andExpect(jsonPath("$.score").value(25))
                .andExpect(jsonPath("$.questions[*].correct", contains(false, true, false, false)))
                .andExpect(jsonPath("$.questions[3].answer").doesNotExist());
        attempt(get("/api/public/attempts/" + binh.id + "/result"), binh.token)
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(25));

        // Editing the quiz after publishing does not change the assignment (it uses the snapshot).
        perform(s.teacher, put("/api/quizzes/" + s.quizId), """
                {"title": "Khác", "questions": [{"kind": "short_text", "prompt": "?", "acceptedAnswers": ["a"]}]}""")
                .andExpect(status().isOk());

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        perform(s.teacher, get("/api/assignments/" + s.assignmentId + "/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentCount").value(3))
                .andExpect(jsonPath("$.submittedCount").value(2))
                .andExpect(jsonPath("$.inProgressCount").value(0))
                .andExpect(jsonPath("$.averageScore").value(62.5))
                .andExpect(jsonPath("$.questions", hasSize(4)))
                .andExpect(jsonPath("$.questions[0].prompt").value("Thủ đô của Việt Nam?"))
                .andExpect(jsonPath("$.questions[*].correct", contains(1, 2, 1, 1)))
                .andExpect(jsonPath("$.questions[*].answered", contains(2, 2, 2, 1)))
                .andExpect(jsonPath("$.questions[*].correctRate", contains(50.0, 100.0, 50.0, 50.0)))
                .andExpect(jsonPath("$.students[*].name", contains("An", "Bình", "Chi")))
                .andExpect(jsonPath("$.students[*].status", contains("submitted", "submitted", "not_started")))
                .andExpect(jsonPath("$.students[*].score", contains(100, 25, null)));
        // Same number of queries whatever the class size (no N+1).
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(5);

        perform(s.teacher, get("/api/assignments/" + s.assignmentId))
                .andExpect(jsonPath("$.questionCount").value(4))
                .andExpect(jsonPath("$.className").value("10A1"));

        // After closing nobody can start or answer any more.
        perform(s.teacher, post("/api/assignments/" + s.assignmentId + "/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("closed"))
                .andExpect(jsonPath("$.closedAt").exists());
        startRequest(code, s.codes.get(2)).andExpect(status().isConflict());
        perform(s.teacher, post("/api/assignments/" + s.assignmentId + "/close")).andExpect(status().isConflict());
        perform(s.teacher, put("/api/assignments/" + s.assignmentId), assignmentJson(s, "immediate", null))
                .andExpect(status().isConflict());
    }

    @Test
    void studentNeverSeesCorrectAnswersBeforeSubmitting() throws Exception {
        Setup s = setUp("immediate");
        Started started = start(publish(s), s.codes.get(0));
        String json = attempt(get("/api/public/attempts/" + started.id), started.token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentName").value("An"))
                .andExpect(jsonPath("$.open").value(true))
                .andExpect(jsonPath("$.questions[2].options", contains("Đà Nẵng", "Gia Lai", "Khánh Hoà")))
                .andReturn().getResponse().getContentAsString();
        assertThat(json).doesNotContain("correct").doesNotContain("Fansipan").doesNotContain("acceptedAnswers");
        attempt(get("/api/public/attempts/" + started.id + "/result"), started.token)
                .andExpect(status().isConflict());
    }

    @Test
    void afterClosePolicyHidesTheScoreUntilTheAssignmentIsClosed() throws Exception {
        Setup s = setUp("after_close");
        Started started = start(publish(s), s.codes.get(0));
        save(started, 0, "0");
        attempt(post("/api/public/attempts/" + started.id + "/submit"), started.token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed").value(false))
                .andExpect(jsonPath("$.score").doesNotExist())
                .andExpect(jsonPath("$.questions", hasSize(0)));

        perform(s.teacher, post("/api/assignments/" + s.assignmentId + "/close")).andExpect(status().isOk());
        attempt(get("/api/public/attempts/" + started.id + "/result"), started.token)
                .andExpect(jsonPath("$.revealed").value(true))
                .andExpect(jsonPath("$.score").value(25));
    }

    @Test
    void wrongCodesAndTokensAreAllNotFound() throws Exception {
        Setup s = setUp("immediate");
        // A draft cannot be started even with the right codes.
        String draftCode = JsonPath.read(perform(s.teacher, get("/api/assignments/" + s.assignmentId))
                .andReturn().getResponse().getContentAsString(), "$.publicCode");
        startRequest(draftCode, s.codes.get(0)).andExpect(status().isNotFound());

        String code = publish(s);
        startRequest(code, "WRONGCODE1").andExpect(status().isNotFound());
        startRequest("0000000000", s.codes.get(0)).andExpect(status().isNotFound());
        // A student of another class cannot use this class's assignment.
        Setup other = setUp("immediate");
        startRequest(code, other.codes.get(0)).andExpect(status().isNotFound());
        // Codes are accepted in lowercase and with spaces.
        startRequest(code.toLowerCase(), " " + s.codes.get(0).toLowerCase() + " ").andExpect(status().isOk());

        Started an = start(code, s.codes.get(0));
        Started binh = start(code, s.codes.get(1));
        attempt(get("/api/public/attempts/" + binh.id), an.token).andExpect(status().isNotFound());
        attempt(get("/api/public/attempts/" + an.id), "made-up-token").andExpect(status().isNotFound());
        mvc.perform(get("/api/public/attempts/" + an.id)).andExpect(status().isNotFound());

        save(an, 9, "0").andExpect(status().isBadRequest());
        save(an, 0, "5").andExpect(status().isBadRequest());
        save(an, 0, "\"Hà Nội\"").andExpect(status().isBadRequest());
        save(an, 2, "[0, 0]").andExpect(status().isBadRequest());
        save(an, 2, "[]").andExpect(status().isBadRequest());
        save(an, 3, "\"   \"").andExpect(status().isBadRequest());
        save(an, 3, "\"" + "x".repeat(201) + "\"").andExpect(status().isBadRequest());
    }

    @Test
    void newAccessCodeInvalidatesTheAttemptInProgress() throws Exception {
        Setup s = setUp("immediate");
        String code = publish(s);
        Started old = start(code, s.codes.get(0));
        save(old, 0, "0").andExpect(status().isOk());

        String json = perform(s.teacher, post("/api/students/" + s.studentIds.get(0) + "/access-code"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String newCode = JsonPath.read(json, "$.accessCode");

        attempt(get("/api/public/attempts/" + old.id), old.token).andExpect(status().isNotFound());
        startRequest(code, s.codes.get(0)).andExpect(status().isNotFound());
        Started fresh = start(code, newCode);
        assertThat(fresh.id).isNotEqualTo(old.id);
        attempt(get("/api/public/attempts/" + fresh.id), fresh.token)
                .andExpect(jsonPath("$.answers").isEmpty());
        assertThat(jdbc.queryForObject("SELECT status FROM attempts WHERE id = ?", String.class, old.id))
                .isEqualTo("invalidated");
    }

    @Test
    void teacherRulesForAssignments() throws Exception {
        Setup s = setUp("immediate");
        String other = signUpTeacher();
        String base = "/api/assignments/" + s.assignmentId;

        perform(other, get(base)).andExpect(status().isNotFound());
        perform(other, get(base + "/analytics")).andExpect(status().isNotFound());
        perform(other, post(base + "/publish")).andExpect(status().isNotFound());
        perform(other, delete(base)).andExpect(status().isNotFound());
        perform(other, get("/api/assignments?classId=" + s.classId)).andExpect(status().isNotFound());
        perform(other, post("/api/assignments"), assignmentJson(s, "immediate", null)).andExpect(status().isNotFound());

        perform(s.teacher, get("/api/assignments?classId=" + s.classId))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].className").value("10A1"))
                .andExpect(jsonPath("$.content[0].status").value("draft"));

        // Schedule checks.
        String past = Instant.now().minus(1, ChronoUnit.DAYS).toString();
        perform(s.teacher, put(base), assignmentJson(s, "immediate", past))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.dueAt").exists());
        perform(s.teacher, post("/api/assignments"), """
                {"classId": %d, "quizId": %d, "title": "x", "opensAt": "2030-01-02T00:00:00Z",
                 "dueAt": "2030-01-01T00:00:00Z"}""".formatted(s.classId, s.quizId))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.dueAt").exists());
        perform(s.teacher, post("/api/assignments"), "{\"title\": \" \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.classId").exists());

        // A published assignment keeps its class and quiz but the deadline can be extended.
        publish(s);
        perform(s.teacher, post(base + "/publish")).andExpect(status().isConflict());
        String later = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
        perform(s.teacher, put(base), assignmentJson(s, "immediate", later))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dueAt").value(later));
        long otherQuiz = createQuiz(s.teacher);
        perform(s.teacher, put(base), assignmentJson(s.classId, otherQuiz, "immediate", later))
                .andExpect(status().isConflict());

        // Deleting the quiz keeps the published assignment working.
        perform(s.teacher, delete("/api/quizzes/" + s.quizId)).andExpect(status().isNoContent());
        perform(s.teacher, get(base)).andExpect(jsonPath("$.quizId").doesNotExist())
                .andExpect(jsonPath("$.questionCount").value(4));

        perform(s.teacher, delete(base)).andExpect(status().isNoContent());
        perform(s.teacher, get(base)).andExpect(status().isNotFound());
    }

    @Test
    void emptyQuizCannotBePublished() throws Exception {
        Setup s = setUp("immediate");
        perform(s.teacher, put("/api/quizzes/" + s.quizId), "{\"title\": \"Trống\", \"questions\": []}")
                .andExpect(status().isOk());
        perform(s.teacher, post("/api/assignments/" + s.assignmentId + "/publish"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("no questions")));
    }

    @Test
    void retentionJobDeletesAttemptsOlderThan90Days() throws Exception {
        Setup s = setUp("immediate");
        String code = publish(s);
        Started old = start(code, s.codes.get(0));
        save(old, 0, "0");
        Started recent = start(code, s.codes.get(1));
        jdbc.update("UPDATE attempts SET started_at = now() - interval '91 days' WHERE id = ?", old.id);

        assertThat(retentionJob.deleteOldAttempts()).isGreaterThanOrEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attempts WHERE id = ?", Integer.class, old.id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attempt_answers WHERE attempt_id = ?", Integer.class,
                old.id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attempts WHERE id = ?", Integer.class, recent.id))
                .isEqualTo(1);
    }

    // --- helpers ---

    private record Setup(String teacher, long classId, long quizId, long assignmentId, List<Integer> studentIds,
                         List<String> codes) {
    }

    private record Started(long id, String token) {
    }

    /** A teacher with class 10A1 (An, Bình, Chi), the 4-question quiz and a draft assignment. */
    private Setup setUp(String revealPolicy) throws Exception {
        String teacher = signUpTeacher();
        String classJson = perform(teacher, post("/api/classes"), "{\"name\": \"10A1\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long classId = ((Number) JsonPath.read(classJson, "$.id")).longValue();
        String students = perform(teacher, post("/api/classes/" + classId + "/students"),
                "{\"names\": [\"An\", \"Bình\", \"Chi\"]}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long quizId = createQuiz(teacher);
        String assignment = perform(teacher, post("/api/assignments"),
                assignmentJson(classId, quizId, revealPolicy, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("draft"))
                .andExpect(jsonPath("$.questionCount").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        return new Setup(teacher, classId, quizId, ((Number) JsonPath.read(assignment, "$.id")).longValue(),
                JsonPath.read(students, "$[*].id"), JsonPath.read(students, "$[*].accessCode"));
    }

    private long createQuiz(String teacher) throws Exception {
        String json = perform(teacher, post("/api/quizzes"), QUIZ)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private static String assignmentJson(Setup s, String revealPolicy, String dueAt) {
        return assignmentJson(s.classId, s.quizId, revealPolicy, dueAt);
    }

    private static String assignmentJson(long classId, long quizId, String revealPolicy, String dueAt) {
        return """
                {"classId": %d, "quizId": %d, "title": "Kiểm tra 15 phút", "description": "Làm cẩn thận",
                 "revealPolicy": "%s", "dueAt": %s}"""
                .formatted(classId, quizId, revealPolicy, dueAt == null ? "null" : "\"" + dueAt + "\"");
    }

    private String publish(Setup s) throws Exception {
        String json = perform(s.teacher, post("/api/assignments/" + s.assignmentId + "/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("published"))
                .andExpect(jsonPath("$.questionCount").value(4))
                .andExpect(jsonPath("$.publicCode", not(containsString("-"))))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.publicCode");
    }

    private ResultActions startRequest(String publicCode, String accessCode) throws Exception {
        return mvc.perform(post("/api/public/assignments/" + publicCode + "/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accessCode\": \"" + accessCode + "\"}"));
    }

    private Started start(String publicCode, String accessCode) throws Exception {
        String json = startRequest(publicCode, accessCode)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
        return new Started(((Number) JsonPath.read(json, "$.attempt.attemptId")).longValue(),
                JsonPath.read(json, "$.token"));
    }

    private ResultActions attempt(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("X-Attempt-Token", token));
    }

    private ResultActions save(Started attempt, int position, String answerJson) throws Exception {
        return attempt(put("/api/public/attempts/" + attempt.id + "/answers/" + position)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answer\": " + answerJson + "}"), attempt.token);
    }
}
