package com.smartteach.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.smartteach.IntegrationTest;
import com.smartteach.RecordingEmailSender;
import com.smartteach.RecordingEmailSender.Mail;
import com.smartteach.assignment.service.AssignmentDeadlineJob;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** What happens at and before the deadline. Time is moved by editing the rows, not by waiting. */
class AssignmentDeadlineIntegrationTest extends IntegrationTest {

    /** Correct answers: 0, 0, [0, 2], "Fansipan". */
    private static final String QUIZ = """
            {"title": "Địa lý 10", "questions": [
              {"kind": "single", "prompt": "Thủ đô của Việt Nam?",
               "options": [{"label": "Hà Nội", "correct": true}, {"label": "Huế", "correct": false}]},
              {"kind": "true_false", "prompt": "Sông Mê Kông chảy qua Việt Nam.",
               "options": [{"label": "Đúng", "correct": true}, {"label": "Sai", "correct": false}]},
              {"kind": "multi", "prompt": "Tỉnh nào giáp biển?",
               "options": [{"label": "Đà Nẵng", "correct": true}, {"label": "Gia Lai", "correct": false},
                           {"label": "Khánh Hoà", "correct": true}]},
              {"kind": "short_text", "prompt": "Đỉnh núi cao nhất Việt Nam?", "acceptedAnswers": ["Fansipan"]}
            ]}""";
    private static final DateTimeFormatter VIETNAM_TIME =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    @Autowired
    AssignmentDeadlineJob deadlineJob;

    @Autowired
    RecordingEmailSender mails;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void afterTheDeadlineLeftoverAttemptsAreSubmittedAndTheTeacherGetsOneSummary() throws Exception {
        Setup s = setUp(Instant.now().plus(2, ChronoUnit.DAYS));
        Started an = start(s, 0);
        save(an, 0, "0");
        save(an, 1, "0");
        save(an, 2, "[1]");
        Started binh = start(s, 1);
        attempt(post("/api/public/attempts/" + binh.id + "/submit"), binh.token).andExpect(status().isOk());
        // Chi never starts.
        jdbc.update("UPDATE assignments SET due_at = now() - interval '5 minutes' WHERE id = ?", s.assignmentId);
        Instant dueAt = jdbc.queryForObject("SELECT due_at FROM assignments WHERE id = ?", Timestamp.class,
                s.assignmentId).toInstant();

        runJobs();

        attempt(get("/api/public/attempts/" + an.id + "/result"), an.token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("submitted"))
                .andExpect(jsonPath("$.score").value(50));
        // Submitted "at" the deadline, not when the job happened to run.
        assertThat(jdbc.queryForObject("SELECT submitted_at FROM attempts WHERE id = ?", Timestamp.class, an.id)
                .toInstant()).isEqualTo(dueAt);
        save(an, 3, "\"Fansipan\"").andExpect(status().isConflict());

        Mail summary = onlyMail(s);
        assertThat(summary.subject()).isEqualTo("Kết quả bài tập: Kiểm tra 15 phút");
        assertThat(summary.text())
                .contains("lớp 10A1 đã kết thúc lúc " + VIETNAM_TIME.format(dueAt) + " (giờ Việt Nam)")
                .contains("Đã nộp: 2/3 học sinh (1 chưa nộp)")
                .contains("1 bài làm dở được tự động nộp")
                .contains("Điểm trung bình: 25/100")
                .contains("Câu sai nhiều nhất: câu 3 \"Tỉnh nào giáp biển?\", 0% học sinh làm đúng")
                .contains("http://localhost:5173/assignments/" + s.assignmentId)
                .contains("http://localhost:5173/account");

        // The next runs find nothing to do: no second summary.
        runJobs();
        runJobs();
        assertThat(mails.sentTo(s.email)).hasSize(1);
        // Both jobs ran under a ShedLock lock (released, so lock_until is not in the future).
        assertThat(jdbc.queryForList("SELECT name FROM shedlock WHERE lock_until <= now()", String.class))
                .contains("assignment-deadlines", "email-outbox");
    }

    @Test
    void closingSubmitsAttemptsInProgressInTheSameRequest() throws Exception {
        Setup s = setUp(null);
        Started an = start(s, 0);
        save(an, 0, "0");

        perform(s.teacher, post("/api/assignments/" + s.assignmentId + "/close")).andExpect(status().isOk());
        perform(s.teacher, get("/api/assignments/" + s.assignmentId + "/analytics"))
                .andExpect(jsonPath("$.submittedCount").value(1))
                .andExpect(jsonPath("$.inProgressCount").value(0))
                .andExpect(jsonPath("$.averageScore").value(25));

        runJobs();
        assertThat(onlyMail(s).text()).contains("Đã nộp: 1/3 học sinh").contains("Điểm trung bình: 25/100");
    }

    @Test
    void reminderIsSentOnceADayBeforeTheDeadline() throws Exception {
        Setup s = setUp(Instant.now().plus(3, ChronoUnit.DAYS));
        start(s, 0);
        runJobs();
        // Due in three days: too early.
        assertThat(mails.sentTo(s.email)).isEmpty();

        dueIn12HoursAfterTwoDaysOpen(s);
        runJobs();
        runJobs();

        Mail reminder = onlyMail(s);
        assertThat(reminder.subject()).isEqualTo("Sắp hết hạn: Kiểm tra 15 phút");
        assertThat(reminder.text())
                .contains("Còn 3/3 học sinh chưa nộp: 2 chưa bắt đầu, 1 đang làm dở.")
                .contains("Mã bài tập để nhắc học sinh: " + s.publicCode);
    }

    @Test
    void movingTheDeadlineLaterAllowsANewReminder() throws Exception {
        Setup s = setUp(Instant.now().plus(3, ChronoUnit.DAYS));
        dueIn12HoursAfterTwoDaysOpen(s);
        runJobs();
        assertThat(mails.sentTo(s.email)).hasSize(1);

        String json = perform(s.teacher, get("/api/assignments/" + s.assignmentId))
                .andReturn().getResponse().getContentAsString();
        int version = JsonPath.read(json, "$.version");
        perform(s.teacher, put("/api/assignments/" + s.assignmentId), assignmentJson(s.classId, s.quizId,
                "\"" + Instant.now().plus(5, ChronoUnit.DAYS) + "\"").replace("}", ", \"version\": " + version + "}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT reminder_queued_at FROM assignments WHERE id = ?", Timestamp.class,
                s.assignmentId)).isNull();

        dueIn12HoursAfterTwoDaysOpen(s);
        runJobs();
        assertThat(mails.sentTo(s.email)).hasSize(2);
    }

    @Test
    void shortAssignmentsAndFinishedClassesGetNoReminder() throws Exception {
        // Open for only 13 hours: a reminder at "24 hours before" would come before it opened.
        Setup shortOne = setUp(Instant.now().plus(3, ChronoUnit.DAYS));
        jdbc.update("""
                UPDATE assignments SET created_at = now() - interval '1 hour', due_at = now() + interval '12 hours'
                WHERE id = ?""", shortOne.assignmentId);

        // Everyone has submitted already.
        Setup done = setUp(Instant.now().plus(3, ChronoUnit.DAYS));
        for (int i = 0; i < 3; i++) {
            Started started = start(done, i);
            attempt(post("/api/public/attempts/" + started.id + "/submit"), started.token);
        }
        dueIn12HoursAfterTwoDaysOpen(done);

        runJobs();
        assertThat(mails.sentTo(shortOne.email)).isEmpty();
        assertThat(mails.sentTo(done.email)).isEmpty();
    }

    @Test
    void teacherCanTurnTheMailsOff() throws Exception {
        Setup s = setUp(Instant.now().plus(3, ChronoUnit.DAYS));
        perform(s.teacher, get("/api/me")).andExpect(jsonPath("$.emailNotifications").value(true));
        perform(s.teacher, put("/api/me/settings"), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.emailNotifications").exists());
        perform(s.teacher, put("/api/me/settings"), "{\"emailNotifications\": false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailNotifications").value(false));
        perform(s.teacher, get("/api/me")).andExpect(jsonPath("$.emailNotifications").value(false));

        start(s, 0);
        dueIn12HoursAfterTwoDaysOpen(s);
        runJobs();
        perform(s.teacher, post("/api/assignments/" + s.assignmentId + "/close")).andExpect(status().isOk());
        runJobs();

        assertThat(mails.sentTo(s.email)).isEmpty();
        // The work itself still happens.
        perform(s.teacher, get("/api/assignments/" + s.assignmentId + "/analytics"))
                .andExpect(jsonPath("$.inProgressCount").value(0))
                .andExpect(jsonPath("$.submittedCount").value(1));
    }

    @Test
    void settingsNeedASignedInUser() throws Exception {
        mvc.perform(put("/api/me/settings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailNotifications\": false}")).andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private record Setup(String teacher, String email, long classId, long quizId, long assignmentId,
                         String publicCode, List<String> codes) {
    }

    private record Started(long id, String token) {
    }

    /** Deadline job, then the outbox job, as they run in the background. */
    private void runJobs() {
        deadlineJob.run();
        sendQueuedMails();
    }

    private Mail onlyMail(Setup s) {
        List<Mail> sent = mails.sentTo(s.email);
        assertThat(sent).hasSize(1);
        return sent.getFirst();
    }

    private void dueIn12HoursAfterTwoDaysOpen(Setup s) {
        jdbc.update("""
                UPDATE assignments SET created_at = now() - interval '2 days', due_at = now() + interval '12 hours'
                WHERE id = ?""", s.assignmentId);
    }

    /** A teacher with class 10A1 (An, Bình, Chi) and a published assignment of the 4-question quiz. */
    private Setup setUp(Instant dueAt) throws Exception {
        String teacher = signUpTeacher();
        String email = JsonPath.read(perform(teacher, get("/api/me")).andReturn().getResponse().getContentAsString(),
                "$.email");
        long classId = id(perform(teacher, post("/api/classes"), "{\"name\": \"10A1\"}"));
        String students = perform(teacher, post("/api/classes/" + classId + "/students"),
                "{\"names\": [\"An\", \"Bình\", \"Chi\"]}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long quizId = id(perform(teacher, post("/api/quizzes"), QUIZ));
        long assignmentId = id(perform(teacher, post("/api/assignments"),
                assignmentJson(classId, quizId, dueAt == null ? "null" : "\"" + dueAt + "\"")));
        String published = perform(teacher, post("/api/assignments/" + assignmentId + "/publish"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Setup(teacher, email, classId, quizId, assignmentId, JsonPath.read(published, "$.publicCode"),
                JsonPath.read(students, "$[*].accessCode"));
    }

    private static String assignmentJson(long classId, long quizId, String dueAt) {
        return """
                {"classId": %d, "quizId": %d, "title": "Kiểm tra 15 phút", "revealPolicy": "immediate", "dueAt": %s}"""
                .formatted(classId, quizId, dueAt);
    }

    private static long id(ResultActions created) throws Exception {
        String json = created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private Started start(Setup s, int student) throws Exception {
        String json = mvc.perform(post("/api/public/assignments/" + s.publicCode + "/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessCode\": \"" + s.codes.get(student) + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
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
