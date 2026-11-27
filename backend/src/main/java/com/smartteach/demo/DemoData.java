package com.smartteach.demo;

import com.smartteach.assignment.dto.AssignmentRequest;
import com.smartteach.assignment.dto.AssignmentResponse;
import com.smartteach.assignment.dto.StartAttemptResponse;
import com.smartteach.assignment.entity.RevealPolicy;
import com.smartteach.assignment.service.AssignmentService;
import com.smartteach.assignment.service.AttemptService;
import com.smartteach.classroom.dto.AddStudentsRequest;
import com.smartteach.classroom.dto.ClassRequest;
import com.smartteach.classroom.dto.ClassResponse;
import com.smartteach.classroom.dto.StudentCodeResponse;
import com.smartteach.classroom.service.ClassroomService;
import com.smartteach.classroom.service.StudentService;
import com.smartteach.quiz.dto.OptionRequest;
import com.smartteach.quiz.dto.QuestionRequest;
import com.smartteach.quiz.dto.QuizRequest;
import com.smartteach.quiz.dto.QuizSummaryResponse;
import com.smartteach.quiz.entity.QuestionKind;
import com.smartteach.quiz.service.QuizService;
import com.smartteach.user.entity.Role;
import com.smartteach.user.entity.User;
import com.smartteach.user.service.UserService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Demo content for the live site: a teacher with classes, quizzes and assignments that already
 * have results. Everything goes through the normal services, so the data follows the same rules
 * as data created in the UI (limits, hashed access codes, grading on submit).
 */
@Component
@RequiredArgsConstructor
public class DemoData {

    private static final List<String> NAMES_10A1 = List.of(
            "Nguyễn Văn An", "Trần Thị Bình", "Lê Hoàng Cường", "Phạm Thu Dung", "Hoàng Minh Đức",
            "Vũ Ngọc Hà", "Đặng Quốc Huy", "Bùi Thanh Hương", "Đỗ Gia Khánh", "Ngô Bảo Linh",
            "Dương Tuấn Minh", "Lý Phương Nhi");
    private static final List<String> NAMES_11B2 = List.of(
            "Trịnh Hải Nam", "Mai Anh Thư", "Phan Đức Tài", "Hồ Ngọc Trâm", "Châu Gia Bảo",
            "Tạ Minh Khoa", "La Thảo Vy", "Quách Nhật Long");

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final ClassroomService classroomService;
    private final StudentService studentService;
    private final QuizService quizService;
    private final AssignmentService assignmentService;
    private final AttemptService attemptService;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    /**
     * Deletes the demo teacher's data and creates it again, so whatever visitors changed is gone
     * after a restart. Returns the teacher's id.
     */
    public Long reset(String email, String password) {
        Long teacherId = resetTeacher(email, password);
        // Fixed seed: the demo looks the same after every restart.
        Random random = new Random(2026);
        Instant now = clock.instant().truncatedTo(ChronoUnit.HOURS);

        Long class10 = classroomService.create(teacherId, new ClassRequest("Lớp 10A1")).id();
        Long class11 = classroomService.create(teacherId, new ClassRequest("Lớp 11B2")).id();
        List<StudentCodeResponse> students10 = studentService.addAll(teacherId, class10, new AddStudentsRequest(NAMES_10A1));
        List<StudentCodeResponse> students11 = studentService.addAll(teacherId, class11, new AddStudentsRequest(NAMES_11B2));

        QuizRequest geography = geographyQuiz();
        QuizRequest history = historyQuiz();
        Long geographyId = quizService.create(teacherId, geography).id();
        Long historyId = quizService.create(teacherId, history).id();
        Long mathId = quizService.create(teacherId, mathQuiz()).id();

        // Open, results visible right away: 9 submitted, 1 in progress, 2 not started.
        AssignmentResponse review = publish(teacherId, new AssignmentRequest(class10, geographyId,
                "Ôn tập Địa lý – tuần 12", "Làm trước buổi học thứ Hai. Được làm lại khi chưa nộp.",
                now.minus(Duration.ofDays(2)), now.plus(Duration.ofDays(5)), RevealPolicy.IMMEDIATE));
        takeAssignment(review.publicCode(), students10, geography, 9, 1, random);

        // Closed by the teacher, so the scores are now visible to students.
        AssignmentResponse test = publish(teacherId, new AssignmentRequest(class11, historyId,
                "Kiểm tra 15 phút – Lịch sử", "", now.minus(Duration.ofDays(6)), now.plus(Duration.ofDays(1)),
                RevealPolicy.AFTER_CLOSE));
        takeAssignment(test.publicCode(), students11, history, 7, 0, random);
        assignmentService.close(teacherId, test.id());

        // A draft that students cannot see yet.
        assignmentService.create(teacherId, new AssignmentRequest(class10, mathId, "Bài tập về nhà – Mệnh đề",
                "Đọc lại SGK trang 5–8 trước khi làm.", now.plus(Duration.ofDays(1)), now.plus(Duration.ofDays(8)),
                RevealPolicy.IMMEDIATE));
        return teacherId;
    }

    private Long resetTeacher(String email, String password) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        User teacher = userService.findByEmail(normalized)
                .orElseGet(() -> new User(normalized, "", "Nguyễn Minh Anh", Role.TEACHER, clock.instant()));
        teacher.setPasswordHash(passwordEncoder.encode(password));
        Long teacherId = userService.save(teacher).getId();

        // Deleting a class also deletes its students, assignments and attempts.
        PageRequest all = PageRequest.of(0, 100);
        for (ClassResponse classroom : classroomService.list(teacherId, all).content()) {
            classroomService.delete(teacherId, classroom.id());
        }
        for (QuizSummaryResponse quiz : quizService.list(teacherId, all).content()) {
            quizService.delete(teacherId, quiz.id());
        }
        return teacherId;
    }

    private AssignmentResponse publish(Long teacherId, AssignmentRequest request) {
        return assignmentService.publish(teacherId, assignmentService.create(teacherId, request).id());
    }

    /** The first students submit, the next ones stop halfway, the rest never open the assignment. */
    private void takeAssignment(String publicCode, List<StudentCodeResponse> students, QuizRequest quiz,
                                int submitted, int inProgress, Random random) {
        List<QuestionRequest> questions = quiz.questions();
        for (int i = 0; i < submitted + inProgress; i++) {
            StartAttemptResponse start = attemptService.start(publicCode, students.get(i).accessCode());
            Long attemptId = start.attempt().attemptId();
            boolean submits = i < submitted;
            double skill = 0.4 + random.nextDouble() * 0.6;
            int answered = submits ? questions.size() : questions.size() / 2;
            for (int position = 0; position < answered; position++) {
                boolean right = random.nextDouble() < skill;
                attemptService.saveAnswer(attemptId, start.token(), position, answer(questions.get(position), right));
            }
            if (submits) {
                attemptService.submit(attemptId, start.token());
            }
        }
    }

    /** Builds the answer a student would send: an option index, a list of indexes or a text. */
    private JsonNode answer(QuestionRequest question, boolean right) {
        List<Integer> correct = IntStream.range(0, question.options().size())
                .filter(i -> question.options().get(i).correct())
                .boxed()
                .toList();
        Object value = switch (question.kind()) {
            case SINGLE, TRUE_FALSE -> right ? correct.getFirst() : (correct.getFirst() + 1) % question.options().size();
            // A wrong multi answer picks only part of the correct set.
            case MULTI -> right ? correct : List.of(correct.getFirst());
            case SHORT_TEXT -> right ? question.acceptedAnswers().getFirst() : "Em chưa nhớ";
        };
        return jsonMapper.valueToTree(value);
    }

    private static QuizRequest geographyQuiz() {
        return new QuizRequest("Địa lý Việt Nam – Bài 1", List.of(
                single("Thủ đô của Việt Nam là thành phố nào?", 0, "Hà Nội", "TP. Hồ Chí Minh", "Đà Nẵng", "Huế"),
                trueFalse("Việt Nam có đường bờ biển dài hơn 3.000 km.", true),
                multi("Những tỉnh, thành phố nào giáp biển?", List.of(0, 2, 3),
                        "Đà Nẵng", "Lai Châu", "Khánh Hoà", "Quảng Ninh", "Cao Bằng"),
                text("Sông dài nhất chảy qua Việt Nam là sông gì?", "Mê Kông", "Mekong", "Cửu Long"),
                single("Đỉnh núi cao nhất Việt Nam là?", 0, "Fansipan", "Bạch Mã", "Bà Đen", "Ngọc Linh")));
    }

    private static QuizRequest historyQuiz() {
        return new QuizRequest("Lịch sử 11 – Kiểm tra 15 phút", List.of(
                single("Cách mạng tháng Tám thành công vào năm nào?", 2, "1930", "1941", "1945", "1954"),
                trueFalse("Chiến dịch Điện Biên Phủ kết thúc vào năm 1954.", true),
                text("Ai đọc bản Tuyên ngôn Độc lập ngày 2/9/1945?", "Hồ Chí Minh", "Bác Hồ"),
                multi("Những sự kiện nào diễn ra trong thế kỷ XX?", List.of(0, 2),
                        "Cách mạng tháng Tám", "Chiến thắng Bạch Đằng năm 938", "Giải phóng miền Nam",
                        "Khởi nghĩa Hai Bà Trưng")));
    }

    private static QuizRequest mathQuiz() {
        return new QuizRequest("Toán 10 – Mệnh đề", List.of(
                trueFalse("“Số 2 là số nguyên tố” là một mệnh đề đúng.", true),
                single("Phủ định của mệnh đề “x > 3” là?", 0, "x ≤ 3", "x < 3", "x ≥ 3", "x = 3"),
                text("2 mũ 5 bằng bao nhiêu?", "32")));
    }

    private static QuestionRequest single(String prompt, int correct, String... labels) {
        return question(QuestionKind.SINGLE, prompt, List.of(correct), labels);
    }

    private static QuestionRequest multi(String prompt, List<Integer> correct, String... labels) {
        return question(QuestionKind.MULTI, prompt, correct, labels);
    }

    private static QuestionRequest trueFalse(String prompt, boolean answer) {
        return question(QuestionKind.TRUE_FALSE, prompt, List.of(answer ? 0 : 1), "Đúng", "Sai");
    }

    private static QuestionRequest text(String prompt, String... accepted) {
        return new QuestionRequest(QuestionKind.SHORT_TEXT, prompt, 30, true, null, List.of(accepted));
    }

    private static QuestionRequest question(QuestionKind kind, String prompt, List<Integer> correct, String... labels) {
        List<OptionRequest> options = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            options.add(new OptionRequest(labels[i], correct.contains(i)));
        }
        return new QuestionRequest(kind, prompt, 20, false, options, null);
    }
}
