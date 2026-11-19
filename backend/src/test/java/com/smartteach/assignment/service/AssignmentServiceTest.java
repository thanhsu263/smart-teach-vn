package com.smartteach.assignment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartteach.assignment.dto.AssignmentRequest;
import com.smartteach.assignment.dto.AssignmentResponse;
import com.smartteach.assignment.entity.Assignment;
import com.smartteach.assignment.entity.AssignmentStatus;
import com.smartteach.assignment.entity.RevealPolicy;
import com.smartteach.assignment.mapper.AssignmentMapperImpl;
import com.smartteach.assignment.repository.AssignmentRepository;
import com.smartteach.classroom.entity.Classroom;
import com.smartteach.classroom.repository.ClassroomRepository;
import com.smartteach.common.exception.ApiException;
import com.smartteach.common.exception.ValidationFailedException;
import com.smartteach.quiz.dto.OptionResponse;
import com.smartteach.quiz.dto.QuestionResponse;
import com.smartteach.quiz.dto.QuizResponse;
import com.smartteach.quiz.entity.QuestionKind;
import com.smartteach.quiz.service.QuizService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-20T08:00:00Z");
    private static final long OWNER = 7L;
    private static final long CLASS_ID = 2L;
    private static final long QUIZ_ID = 3L;
    private static final long ASSIGNMENT_ID = 5L;

    @Mock
    AssignmentRepository assignmentRepository;

    @Mock
    ClassroomRepository classroomRepository;

    @Mock
    QuizService quizService;

    private final QuizSnapshots snapshots = new QuizSnapshots(JsonMapper.builder().build());
    private AssignmentService service;

    @BeforeEach
    void setUp() {
        service = new AssignmentService(assignmentRepository, classroomRepository, quizService, snapshots,
                new AssignmentMapperImpl(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createMakesADraftWithAPublicCode() {
        when(assignmentRepository.countByOwnerId(OWNER)).thenReturn(0L);
        when(assignmentRepository.existsByPublicCode(anyString())).thenReturn(false);
        when(classroomRepository.findByIdAndOwnerId(CLASS_ID, OWNER)).thenReturn(Optional.of(classroom()));
        when(classroomRepository.findById(CLASS_ID)).thenReturn(Optional.of(classroom()));
        when(assignmentRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        AssignmentResponse response = service.create(OWNER, request(null, NOW.plus(Duration.ofDays(7))));

        assertThat(response.status()).isEqualTo(AssignmentStatus.DRAFT);
        assertThat(response.publicCode()).matches("[0-9A-F]{10}");
        assertThat(response.title()).isEqualTo("Kiểm tra 15 phút");
        assertThat(response.className()).isEqualTo("10A1");
        assertThat(response.questionCount()).isNull();
        verify(quizService).requireOwned(OWNER, QUIZ_ID);
    }

    @Test
    void createStopsAt100Assignments() {
        when(assignmentRepository.countByOwnerId(OWNER)).thenReturn(100L);

        assertStatus(() -> service.create(OWNER, request(null, null)), HttpStatus.CONFLICT);
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void dueDateMustBeAfterTheOpeningDateAndInTheFuture() {
        when(assignmentRepository.countByOwnerId(OWNER)).thenReturn(0L);

        assertThatThrownBy(() -> service.create(OWNER, request(NOW.plusSeconds(60), NOW.plusSeconds(60))))
                .isInstanceOfSatisfying(ValidationFailedException.class,
                        ex -> assertThat(ex.getErrors()).containsKey("dueAt"));
        assertThatThrownBy(() -> service.create(OWNER, request(null, NOW.minusSeconds(1))))
                .isInstanceOfSatisfying(ValidationFailedException.class,
                        ex -> assertThat(ex.getErrors()).containsEntry("dueAt", "The due date must be in the future"));
    }

    @Test
    void aPastDueDateThatWasAlreadySavedCanStay() {
        Assignment assignment = draft();
        Instant pastDue = NOW.minus(Duration.ofDays(1));
        assignment.setDueAt(pastDue);
        assignment.setStatus(AssignmentStatus.CLOSED);
        when(assignmentRepository.findByIdAndOwnerIdForUpdate(ASSIGNMENT_ID, OWNER)).thenReturn(Optional.of(assignment));

        // Closed is rejected before the schedule is checked; reopen as published to reach the check.
        assertStatus(() -> service.update(OWNER, ASSIGNMENT_ID, request(null, pastDue)), HttpStatus.CONFLICT);

        assignment.setStatus(AssignmentStatus.PUBLISHED);
        when(classroomRepository.findById(CLASS_ID)).thenReturn(Optional.of(classroom()));
        AssignmentResponse response = service.update(OWNER, ASSIGNMENT_ID, request(null, pastDue));
        assertThat(response.dueAt()).isEqualTo(pastDue);
        assertThat(response.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void classAndQuizAreFixedAfterPublishing() {
        Assignment assignment = draft();
        assignment.setStatus(AssignmentStatus.PUBLISHED);
        when(assignmentRepository.findByIdAndOwnerIdForUpdate(ASSIGNMENT_ID, OWNER)).thenReturn(Optional.of(assignment));

        AssignmentRequest otherClass = new AssignmentRequest(99L, QUIZ_ID, "Bài", null, null, null, null);
        AssignmentRequest otherQuiz = new AssignmentRequest(CLASS_ID, 99L, "Bài", null, null, null, null);

        assertStatus(() -> service.update(OWNER, ASSIGNMENT_ID, otherClass), HttpStatus.CONFLICT);
        assertStatus(() -> service.update(OWNER, ASSIGNMENT_ID, otherQuiz), HttpStatus.CONFLICT);
    }

    @Test
    void publishCopiesTheQuizIntoTheSnapshot() {
        Assignment assignment = draft();
        when(assignmentRepository.findByIdAndOwnerIdForUpdate(ASSIGNMENT_ID, OWNER)).thenReturn(Optional.of(assignment));
        when(quizService.get(OWNER, QUIZ_ID)).thenReturn(quiz(List.of(question())));
        when(classroomRepository.findById(CLASS_ID)).thenReturn(Optional.of(classroom()));

        AssignmentResponse response = service.publish(OWNER, ASSIGNMENT_ID);

        assertThat(response.status()).isEqualTo(AssignmentStatus.PUBLISHED);
        assertThat(response.questionCount()).isEqualTo(1);
        assertThat(snapshots.read(assignment.getQuizSnapshot()).questions().getFirst().prompt())
                .isEqualTo("Thủ đô của Việt Nam?");
    }

    @Test
    void publishRules() {
        Assignment assignment = draft();
        when(assignmentRepository.findByIdAndOwnerIdForUpdate(ASSIGNMENT_ID, OWNER)).thenReturn(Optional.of(assignment));

        when(quizService.get(OWNER, QUIZ_ID)).thenReturn(quiz(List.of()));
        assertStatus(() -> service.publish(OWNER, ASSIGNMENT_ID), HttpStatus.CONFLICT);   // no questions

        assignment.setDueAt(NOW);
        assertStatus(() -> service.publish(OWNER, ASSIGNMENT_ID), HttpStatus.CONFLICT);   // due date passed

        assignment.setQuizId(null);
        assertStatus(() -> service.publish(OWNER, ASSIGNMENT_ID), HttpStatus.CONFLICT);   // quiz deleted

        assignment.setStatus(AssignmentStatus.PUBLISHED);
        assertStatus(() -> service.publish(OWNER, ASSIGNMENT_ID), HttpStatus.CONFLICT);   // not a draft
        assertThat(assignment.getQuizSnapshot()).isNull();
    }

    @Test
    void onlyAPublishedAssignmentCanBeClosed() {
        Assignment assignment = draft();
        when(assignmentRepository.findByIdAndOwnerIdForUpdate(ASSIGNMENT_ID, OWNER)).thenReturn(Optional.of(assignment));
        assertStatus(() -> service.close(OWNER, ASSIGNMENT_ID), HttpStatus.CONFLICT);

        assignment.setStatus(AssignmentStatus.PUBLISHED);
        when(classroomRepository.findById(CLASS_ID)).thenReturn(Optional.of(classroom()));
        AssignmentResponse response = service.close(OWNER, ASSIGNMENT_ID);
        assertThat(response.status()).isEqualTo(AssignmentStatus.CLOSED);
        assertThat(response.closedAt()).isEqualTo(NOW);
    }

    @Test
    void anotherTeachersAssignmentIsNotFound() {
        when(assignmentRepository.findByIdAndOwnerId(ASSIGNMENT_ID, OWNER)).thenReturn(Optional.empty());
        when(assignmentRepository.findByIdAndOwnerIdForUpdate(ASSIGNMENT_ID, OWNER)).thenReturn(Optional.empty());

        assertStatus(() -> service.get(OWNER, ASSIGNMENT_ID), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.delete(OWNER, ASSIGNMENT_ID), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.publish(OWNER, ASSIGNMENT_ID), HttpStatus.NOT_FOUND);
    }

    private static void assertStatus(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getStatus()).isEqualTo(status));
    }

    private static AssignmentRequest request(Instant opensAt, Instant dueAt) {
        return new AssignmentRequest(CLASS_ID, QUIZ_ID, "  Kiểm tra 15 phút ", null, opensAt, dueAt,
                RevealPolicy.IMMEDIATE);
    }

    private static Assignment draft() {
        Assignment assignment = new Assignment(OWNER, "ABCDEF0123", NOW.minus(Duration.ofDays(1)));
        ReflectionTestUtils.setField(assignment, "id", ASSIGNMENT_ID);
        assignment.setClassId(CLASS_ID);
        assignment.setQuizId(QUIZ_ID);
        assignment.setTitle("Bài");
        assignment.setDescription("");
        assignment.setRevealPolicy(RevealPolicy.AFTER_CLOSE);
        return assignment;
    }

    private static Classroom classroom() {
        Classroom classroom = new Classroom(OWNER, "10A1", NOW);
        ReflectionTestUtils.setField(classroom, "id", CLASS_ID);
        return classroom;
    }

    private static QuizResponse quiz(List<QuestionResponse> questions) {
        return new QuizResponse(QUIZ_ID, "Địa lý", NOW, NOW, questions);
    }

    private static QuestionResponse question() {
        return new QuestionResponse(0, QuestionKind.SINGLE, "Thủ đô của Việt Nam?", 20, false,
                List.of(new OptionResponse("Hà Nội", true), new OptionResponse("Huế", false)), List.of());
    }
}
