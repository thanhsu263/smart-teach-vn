package com.smartteach.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartteach.IntegrationTest;
import com.smartteach.assignment.dto.AnalyticsResponse;
import com.smartteach.assignment.dto.AssignmentSummaryResponse;
import com.smartteach.assignment.entity.AssignmentStatus;
import com.smartteach.assignment.service.AnalyticsService;
import com.smartteach.assignment.service.AssignmentService;
import com.smartteach.classroom.service.ClassroomService;
import com.smartteach.quiz.service.QuizService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class DemoDataTest extends IntegrationTest {

    private static final PageRequest ALL = PageRequest.of(0, 100);

    @Autowired
    private DemoData demoData;
    @Autowired
    private ClassroomService classroomService;
    @Autowired
    private QuizService quizService;
    @Autowired
    private AssignmentService assignmentService;
    @Autowired
    private AnalyticsService analyticsService;

    @Test
    void resetCreatesDemoContentAndReplacesItOnTheNextRun() throws Exception {
        String email = "demo-" + UUID.randomUUID() + "@example.com";

        Long first = demoData.reset(email, "first-password");
        Long teacherId = demoData.reset(email, "second-password");

        assertThat(teacherId).isEqualTo(first);
        assertThat(classroomService.list(teacherId, ALL).content()).hasSize(2);
        assertThat(quizService.list(teacherId, ALL).content()).hasSize(3);

        List<AssignmentSummaryResponse> assignments = assignmentService.list(teacherId, null, ALL).content();
        Map<String, AssignmentSummaryResponse> byTitle = assignments.stream()
                .collect(Collectors.toMap(AssignmentSummaryResponse::title, Function.identity()));
        assertThat(byTitle).hasSize(3);
        assertThat(byTitle.get("Ôn tập Địa lý – tuần 12").status()).isEqualTo(AssignmentStatus.PUBLISHED);
        assertThat(byTitle.get("Kiểm tra 15 phút – Lịch sử").status()).isEqualTo(AssignmentStatus.CLOSED);
        assertThat(byTitle.get("Bài tập về nhà – Mệnh đề").status()).isEqualTo(AssignmentStatus.DRAFT);

        AnalyticsResponse review = analyticsService.analytics(teacherId, byTitle.get("Ôn tập Địa lý – tuần 12").id());
        assertThat(review.studentCount()).isEqualTo(12);
        assertThat(review.submittedCount()).isEqualTo(9);
        assertThat(review.inProgressCount()).isEqualTo(1);
        assertThat(review.averageScore()).isNotNull();

        // The password is replaced on every reset.
        login(email, "first-password").andExpect(status().isUnauthorized());
        login(email, "second-password").andExpect(status().isOk());
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }
}
