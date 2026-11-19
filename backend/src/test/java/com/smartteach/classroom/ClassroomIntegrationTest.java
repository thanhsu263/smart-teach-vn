package com.smartteach.classroom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.smartteach.IntegrationTest;
import com.smartteach.classroom.repository.StudentRepository;
import com.smartteach.classroom.service.AccessCodes;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ClassroomIntegrationTest extends IntegrationTest {

    @Autowired
    StudentRepository studentRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void teacherCanCreateListGetRenameAndDeleteClass() throws Exception {
        String teacher = signUpTeacher();
        long id = createClass(teacher, "  10A1  ");

        perform(teacher, get("/api/classes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].name").value("10A1"))
                .andExpect(jsonPath("$.content[0].studentCount").value(0));

        perform(teacher, put("/api/classes/" + id), "{\"name\":\"Lớp 10A1\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lớp 10A1"));
        perform(teacher, get("/api/classes/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lớp 10A1"));

        perform(teacher, delete("/api/classes/" + id)).andExpect(status().isNoContent());
        perform(teacher, get("/api/classes/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void listIsNewestFirstAndPageSizeIsCapped() throws Exception {
        String teacher = signUpTeacher();
        createClass(teacher, "Cũ");
        long newest = createClass(teacher, "Mới");

        perform(teacher, get("/api/classes?size=1"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(newest))
                .andExpect(jsonPath("$.totalPages").value(2));
        perform(teacher, get("/api/classes?size=500"))
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void anotherTeachersClassAndStudentsAreNotFound() throws Exception {
        String owner = signUpTeacher();
        long classId = createClass(owner, "10A2");
        long studentId = studentIds(addStudents(owner, classId, "An")).getFirst();

        String other = signUpTeacher();
        perform(other, get("/api/classes")).andExpect(jsonPath("$.totalElements").value(0));
        perform(other, get("/api/classes/" + classId)).andExpect(status().isNotFound());
        perform(other, put("/api/classes/" + classId), "{\"name\":\"Hacked\"}").andExpect(status().isNotFound());
        perform(other, delete("/api/classes/" + classId)).andExpect(status().isNotFound());
        perform(other, get("/api/classes/" + classId + "/students")).andExpect(status().isNotFound());
        perform(other, post("/api/classes/" + classId + "/students"), "{\"names\":[\"X\"]}")
                .andExpect(status().isNotFound());
        perform(other, put("/api/students/" + studentId), "{\"name\":\"X\"}").andExpect(status().isNotFound());
        perform(other, post("/api/students/" + studentId + "/access-code")).andExpect(status().isNotFound());
        perform(other, delete("/api/students/" + studentId)).andExpect(status().isNotFound());

        // Nothing changed for the owner.
        perform(owner, get("/api/classes/" + classId))
                .andExpect(jsonPath("$.name").value("10A2"))
                .andExpect(jsonPath("$.studentCount").value(1));
        perform(owner, get("/api/classes/" + classId + "/students"))
                .andExpect(jsonPath("$[0].name").value("An"));
    }

    @Test
    void addStudentsReturnsCodesOnceAndStoresOnlyHashes() throws Exception {
        String teacher = signUpTeacher();
        long classId = createClass(teacher, "11B");

        String created = addStudents(teacher, classId, " Nguyễn Văn An ", "Trần Thị Bình");
        List<String> codes = JsonPath.read(created, "$[*].accessCode");
        List<String> hints = JsonPath.read(created, "$[*].codeHint");
        assertThat(codes).hasSize(2).allMatch(code -> code.matches("[A-Z2-9]{10}"));
        assertThat(hints.getFirst()).isEqualTo(codes.getFirst().substring(6));

        long firstId = studentIds(created).getFirst();
        assertThat(studentRepository.findById(firstId).orElseThrow().getAccessCodeHash())
                .isEqualTo(AccessCodes.hash(codes.getFirst()))
                .isNotEqualTo(codes.getFirst());

        // A second batch is appended after the first one.
        addStudents(teacher, classId, "Lê Văn Cường");
        perform(teacher, get("/api/classes/" + classId + "/students"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].name").value("Nguyễn Văn An"))
                .andExpect(jsonPath("$[0].position").value(0))
                .andExpect(jsonPath("$[2].name").value("Lê Văn Cường"))
                .andExpect(jsonPath("$[2].position").value(2))
                .andExpect(jsonPath("$[0].codeHint").value(hints.getFirst()))
                .andExpect(jsonPath("$[0].accessCode").doesNotExist());
        perform(teacher, get("/api/classes/" + classId)).andExpect(jsonPath("$.studentCount").value(3));
    }

    @Test
    void regeneratingCodeReplacesTheOldOne() throws Exception {
        String teacher = signUpTeacher();
        long classId = createClass(teacher, "12C");
        String created = addStudents(teacher, classId, "An");
        long studentId = studentIds(created).getFirst();
        String oldCode = JsonPath.read(created, "$[0].accessCode");

        String json = perform(teacher, post("/api/students/" + studentId + "/access-code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(studentId))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String newCode = JsonPath.read(json, "$.accessCode");

        assertThat(newCode).isNotEqualTo(oldCode);
        assertThat(studentRepository.findById(studentId).orElseThrow().getAccessCodeHash())
                .isEqualTo(AccessCodes.hash(newCode));
    }

    @Test
    void renameAndDeleteStudent() throws Exception {
        String teacher = signUpTeacher();
        long classId = createClass(teacher, "10A3");
        List<Long> ids = studentIds(addStudents(teacher, classId, "Anh", "Bảo"));

        perform(teacher, put("/api/students/" + ids.getFirst()), "{\"name\":\"Ánh\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ánh"));
        perform(teacher, delete("/api/students/" + ids.getLast())).andExpect(status().isNoContent());

        perform(teacher, get("/api/classes/" + classId + "/students"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Ánh"));
    }

    @Test
    void deletingStudentOrClassAlsoRemovesTheirAttempts() throws Exception {
        String teacher = signUpTeacher();
        long classId = createClass(teacher, "10A4");
        List<Long> ids = studentIds(addStudents(teacher, classId, "An", "Bình"));
        long assignmentId = insertAssignment(classId);
        insertAttempt(assignmentId, ids.getFirst());
        insertAttempt(assignmentId, ids.getLast());

        perform(teacher, delete("/api/students/" + ids.getFirst())).andExpect(status().isNoContent());
        assertThat(countAttempts(assignmentId)).isEqualTo(1);

        perform(teacher, delete("/api/classes/" + classId)).andExpect(status().isNoContent());
        assertThat(countAttempts(assignmentId)).isZero();
        assertThat(studentRepository.findById(ids.getLast())).isEmpty();
    }

    @Test
    void invalidInputReturnsFieldErrors() throws Exception {
        String teacher = signUpTeacher();
        perform(teacher, post("/api/classes"), "{\"name\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
        perform(teacher, post("/api/classes"), "{\"name\":\"" + "x".repeat(101) + "\"}")
                .andExpect(status().isBadRequest());

        long classId = createClass(teacher, "10A5");
        perform(teacher, post("/api/classes/" + classId + "/students"), "{\"names\":[]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.names").exists());
        perform(teacher, post("/api/classes/" + classId + "/students"), "{\"names\":[\"An\",\" \"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['names[1]']").exists());
        perform(teacher, get("/api/classes/" + classId)).andExpect(jsonPath("$.studentCount").value(0));
    }

    @Test
    void limitsAreEnforced() throws Exception {
        String teacher = signUpTeacher();
        long classId = createClass(teacher, "Đông");
        addStudents(teacher, classId, IntStream.range(0, 99).mapToObj(i -> "HS " + i).toArray(String[]::new));
        perform(teacher, post("/api/classes/" + classId + "/students"), "{\"names\":[\"A\",\"B\"]}")
                .andExpect(status().isConflict());
        addStudents(teacher, classId, "Thứ 100");

        for (int i = 1; i < 20; i++) {
            createClass(teacher, "Lớp " + i);
        }
        perform(teacher, post("/api/classes"), "{\"name\":\"Lớp 21\"}").andExpect(status().isConflict());
    }

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/classes")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/students/1/access-code")).andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private long createClass(String auth, String name) throws Exception {
        String json = perform(auth, post("/api/classes"), "{\"name\":\"" + name + "\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private String addStudents(String auth, long classId, String... names) throws Exception {
        String body = "{\"names\":[" + String.join(",", Arrays.stream(names)
                .map(n -> "\"" + n + "\"").toList()) + "]}";
        return perform(auth, post("/api/classes/" + classId + "/students"), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(names.length)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static List<Long> studentIds(String json) {
        List<Number> ids = JsonPath.read(json, "$[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    // Assignments and attempts have no API yet (Phase 4), so they are inserted directly.
    private long insertAssignment(long classId) {
        Long ownerId = jdbc.queryForObject("SELECT owner_id FROM classes WHERE id = ?", Long.class, classId);
        String code = String.format("%010X", System.nanoTime() & 0xFFFFFFFFFFL);
        jdbc.update("INSERT INTO assignments (owner_id, class_id, title, public_code) VALUES (?, ?, N'Bài 1', ?)",
                ownerId, classId, code);
        return jdbc.queryForObject("SELECT id FROM assignments WHERE public_code = ?", Long.class, code);
    }

    private void insertAttempt(long assignmentId, long studentId) {
        jdbc.update("INSERT INTO attempts (assignment_id, student_id) VALUES (?, ?)", assignmentId, studentId);
        Long attemptId = jdbc.queryForObject(
                "SELECT id FROM attempts WHERE assignment_id = ? AND student_id = ?", Long.class, assignmentId, studentId);
        jdbc.update("INSERT INTO attempt_answers (attempt_id, position, answer) VALUES (?, 0, N'A')", attemptId);
    }

    private int countAttempts(long assignmentId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM attempts WHERE assignment_id = ?", Integer.class, assignmentId);
    }
}
