package com.smartteach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Catches N+1 queries: every list and analytics endpoint must send the same number of SQL
 * statements whether a teacher has a little data or twice as much.
 */
class QueryCountIntegrationTest extends IntegrationTest {

    private static final String QUIZ = """
            {"title": "Kiểm tra", "questions": [
              {"kind": "single", "prompt": "1 + 1?",
               "options": [{"label": "2", "correct": true}, {"label": "3", "correct": false}]},
              {"kind": "multi", "prompt": "Số chẵn?",
               "options": [{"label": "2", "correct": true}, {"label": "3"}, {"label": "4", "correct": true}]},
              {"kind": "short_text", "prompt": "Thủ đô?", "acceptedAnswers": ["Hà Nội"]}
            ]}""";

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void listAndAnalyticsQueriesDoNotGrowWithTheData() throws Exception {
        // 10 submissions is the minimum for the item analysis, so both sizes run its full path.
        Map<String, Long> small = statementsPerEndpoint(10);
        Map<String, Long> large = statementsPerEndpoint(20);

        assertThat(large).isEqualTo(small);
        // Page of a list = 1 statement; Spring skips the count query when the page is not full.
        // Lists of one class and the analytics also load the owned class or assignment first.
        assertThat(small).isEqualTo(Map.of("classes", 1L, "students", 2L, "quizzes", 1L,
                "assignments", 2L, "analytics", 4L, "question-stats", 4L));
    }

    /**
     * Gives a new teacher {@code rows / 2} classes, quizzes and assignments, a class of {@code rows}
     * students who all submit, plus one still working, then counts the statements of each endpoint.
     */
    private Map<String, Long> statementsPerEndpoint(int rows) throws Exception {
        String teacher = signUpTeacher();
        List<Long> classIds = IntStream.range(0, rows / 2).mapToObj(i -> createClass(teacher)).toList();
        long classId = classIds.getFirst();
        List<String> codes = addStudents(teacher, classId, rows + 1);
        List<Long> quizIds = IntStream.range(0, rows / 2).mapToObj(i -> createQuiz(teacher)).toList();
        List<Long> assignmentIds = quizIds.stream().map(quizId -> createAssignment(teacher, classId, quizId)).toList();

        long assignmentId = assignmentIds.getFirst();
        String publicCode = JsonPath.read(perform(teacher, post("/api/assignments/" + assignmentId + "/publish"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.publicCode");
        for (int i = 0; i < rows; i++) {
            answerAndSubmit(publicCode, codes.get(i), i % 2 == 0);
        }
        start(publicCode, codes.get(rows));

        Map<String, String> endpoints = new LinkedHashMap<>();
        endpoints.put("classes", "/api/classes");
        endpoints.put("students", "/api/classes/" + classId + "/students");
        endpoints.put("quizzes", "/api/quizzes");
        endpoints.put("assignments", "/api/assignments?classId=" + classId);
        endpoints.put("analytics", "/api/assignments/" + assignmentId + "/analytics");
        endpoints.put("question-stats", "/api/assignments/" + assignmentId + "/question-stats");

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map.Entry<String, String> endpoint : endpoints.entrySet()) {
            statistics.clear();
            perform(teacher, get(endpoint.getValue())).andExpect(status().isOk());
            counts.put(endpoint.getKey(), statistics.getPrepareStatementCount());
        }
        return counts;
    }

    private long createClass(String teacher) {
        return id(teacher, "/api/classes", "{\"name\": \"10A1\"}");
    }

    private long createQuiz(String teacher) {
        return id(teacher, "/api/quizzes", QUIZ);
    }

    private long createAssignment(String teacher, long classId, long quizId) {
        return id(teacher, "/api/assignments", """
                {"classId": %d, "quizId": %d, "title": "Bài tập", "revealPolicy": "immediate"}"""
                .formatted(classId, quizId));
    }

    private long id(String teacher, String url, String json) {
        try {
            String body = perform(teacher, post(url), json)
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            return ((Number) JsonPath.read(body, "$.id")).longValue();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> addStudents(String teacher, long classId, int count) throws Exception {
        String names = IntStream.range(0, count).mapToObj(i -> "\"Học sinh " + i + "\"")
                .collect(Collectors.joining(", "));
        String json = perform(teacher, post("/api/classes/" + classId + "/students"), "{\"names\": [" + names + "]}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$[*].accessCode");
    }

    private String[] start(String publicCode, String accessCode) throws Exception {
        String json = mvc.perform(post("/api/public/assignments/" + publicCode + "/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessCode\": \"" + accessCode + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new String[] {JsonPath.read(json, "$.attempt.attemptId").toString(), JsonPath.read(json, "$.token")};
    }

    private void answerAndSubmit(String publicCode, String accessCode, boolean right) throws Exception {
        String[] attempt = start(publicCode, accessCode);
        String[] answers = right ? new String[] {"0", "[0, 2]", "\"Ha Noi\""} : new String[] {"1", "[1]", "\"Huế\""};
        for (int position = 0; position < answers.length; position++) {
            mvc.perform(put("/api/public/attempts/" + attempt[0] + "/answers/" + position)
                            .header("X-Attempt-Token", attempt[1])
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"answer\": " + answers[position] + "}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/public/attempts/" + attempt[0] + "/submit").header("X-Attempt-Token", attempt[1]))
                .andExpect(status().isOk());
    }
}
