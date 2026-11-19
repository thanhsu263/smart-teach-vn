package com.smartteach.assignment.service;

import com.smartteach.assignment.dto.AssignmentRequest;
import com.smartteach.assignment.dto.AssignmentResponse;
import com.smartteach.assignment.dto.AssignmentSummaryResponse;
import com.smartteach.assignment.dto.QuizSnapshot;
import com.smartteach.assignment.entity.Assignment;
import com.smartteach.assignment.entity.AssignmentStatus;
import com.smartteach.assignment.mapper.AssignmentMapper;
import com.smartteach.assignment.repository.AssignmentRepository;
import com.smartteach.classroom.entity.Classroom;
import com.smartteach.classroom.repository.ClassroomRepository;
import com.smartteach.classroom.service.ClassroomService;
import com.smartteach.common.dto.PageResponse;
import com.smartteach.common.exception.ApiException;
import com.smartteach.common.exception.ValidationFailedException;
import com.smartteach.quiz.dto.QuizResponse;
import com.smartteach.quiz.service.QuizService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teacher side of assignments. Life cycle: draft (editable, no questions yet) -> published (quiz
 * copied into a snapshot, students can answer between opensAt and dueAt) -> closed. Another
 * teacher's assignment, class or quiz is a 404.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssignmentService {

    static final int MAX_ASSIGNMENTS_PER_TEACHER = 100;
    private static final int CODE_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final AssignmentRepository assignmentRepository;
    private final ClassroomRepository classroomRepository;
    private final QuizService quizService;
    private final QuizSnapshots snapshots;
    private final AssignmentMapper mapper;
    private final Clock clock;

    /** {@code classId} is optional; when given it must be one of the teacher's classes. */
    public PageResponse<AssignmentSummaryResponse> list(Long ownerId, Long classId, Pageable pageable) {
        if (classId != null) {
            findClass(ownerId, classId);
        }
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
        return PageResponse.from(assignmentRepository.findSummaries(ownerId, classId, page));
    }

    public AssignmentResponse get(Long ownerId, Long assignmentId) {
        return toResponse(findOwned(ownerId, assignmentId));
    }

    @Transactional
    public AssignmentResponse create(Long ownerId, AssignmentRequest request) {
        // Not locked, same reasoning as classes: a race only lets the teacher go one over.
        if (assignmentRepository.countByOwnerId(ownerId) >= MAX_ASSIGNMENTS_PER_TEACHER) {
            throw ApiException.conflict("A teacher can have at most " + MAX_ASSIGNMENTS_PER_TEACHER + " assignments");
        }
        checkSchedule(request, null);
        Instant now = clock.instant();
        Assignment assignment = new Assignment(ownerId, newPublicCode(), now);
        apply(ownerId, assignment, request);
        return toResponse(assignmentRepository.save(assignment));
    }

    /**
     * A draft can change everything. Once published, the class and quiz are fixed (students may
     * already have answered), but the title, description, dates and reveal policy can still change,
     * e.g. to extend the deadline. A closed assignment cannot change.
     */
    @Transactional
    public AssignmentResponse update(Long ownerId, Long assignmentId, AssignmentRequest request) {
        Assignment assignment = findOwnedForUpdate(ownerId, assignmentId);
        if (assignment.getStatus() == AssignmentStatus.CLOSED) {
            throw ApiException.conflict("A closed assignment cannot be edited");
        }
        if (assignment.getStatus() == AssignmentStatus.PUBLISHED
                && (!assignment.getClassId().equals(request.classId())
                    || (assignment.getQuizId() != null && !assignment.getQuizId().equals(request.quizId())))) {
            throw ApiException.conflict("The class and quiz cannot change after publishing");
        }
        checkSchedule(request, assignment.getDueAt());
        apply(ownerId, assignment, request);
        assignment.setUpdatedAt(clock.instant());
        return toResponse(assignment);
    }

    /** Attempts and answers go with it (ON DELETE CASCADE). */
    @Transactional
    public void delete(Long ownerId, Long assignmentId) {
        assignmentRepository.delete(findOwnedForUpdate(ownerId, assignmentId));
    }

    /** Copies the quiz into the snapshot; from now on students answer that copy. */
    @Transactional
    public AssignmentResponse publish(Long ownerId, Long assignmentId) {
        Assignment assignment = findOwnedForUpdate(ownerId, assignmentId);
        if (assignment.getStatus() != AssignmentStatus.DRAFT) {
            throw ApiException.conflict("Only a draft can be published");
        }
        if (assignment.getQuizId() == null) {
            throw ApiException.conflict("The quiz of this assignment was deleted; choose another quiz");
        }
        Instant now = clock.instant();
        if (assignment.getDueAt() != null && !assignment.getDueAt().isAfter(now)) {
            throw ApiException.conflict("The due date has passed; change it before publishing");
        }
        QuizResponse quiz = quizService.get(ownerId, assignment.getQuizId());
        if (quiz.questions().isEmpty()) {
            throw ApiException.conflict("The quiz has no questions");
        }
        assignment.setQuizSnapshot(snapshots.write(new QuizSnapshot(quiz.title(), quiz.questions())));
        assignment.setStatus(AssignmentStatus.PUBLISHED);
        assignment.setUpdatedAt(now);
        return toResponse(assignment);
    }

    /** Stops all answering. Attempts still in progress stay unsubmitted. */
    @Transactional
    public AssignmentResponse close(Long ownerId, Long assignmentId) {
        Assignment assignment = findOwnedForUpdate(ownerId, assignmentId);
        if (assignment.getStatus() != AssignmentStatus.PUBLISHED) {
            throw ApiException.conflict("Only a published assignment can be closed");
        }
        Instant now = clock.instant();
        assignment.setStatus(AssignmentStatus.CLOSED);
        assignment.setClosedAt(now);
        assignment.setUpdatedAt(now);
        return toResponse(assignment);
    }

    Assignment findOwned(Long ownerId, Long assignmentId) {
        return assignmentRepository.findByIdAndOwnerId(assignmentId, ownerId)
                .orElseThrow(AssignmentService::assignmentNotFound);
    }

    private Assignment findOwnedForUpdate(Long ownerId, Long assignmentId) {
        return assignmentRepository.findByIdAndOwnerIdForUpdate(assignmentId, ownerId)
                .orElseThrow(AssignmentService::assignmentNotFound);
    }

    private Classroom findClass(Long ownerId, Long classId) {
        return classroomRepository.findByIdAndOwnerId(classId, ownerId).orElseThrow(ClassroomService::classNotFound);
    }

    private void apply(Long ownerId, Assignment assignment, AssignmentRequest request) {
        if (assignment.getStatus() == AssignmentStatus.DRAFT) {
            findClass(ownerId, request.classId());
            quizService.requireOwned(ownerId, request.quizId());
            assignment.setClassId(request.classId());
            assignment.setQuizId(request.quizId());
        }
        assignment.setTitle(request.title().strip());
        assignment.setDescription(request.description());
        assignment.setOpensAt(request.opensAt());
        assignment.setDueAt(request.dueAt());
        assignment.setRevealPolicy(request.revealPolicy());
    }

    /** A due date that was already saved may stay in the past (e.g. when only the title changes). */
    private void checkSchedule(AssignmentRequest request, Instant savedDueAt) {
        Instant opensAt = request.opensAt();
        Instant dueAt = request.dueAt();
        if (dueAt == null) {
            return;
        }
        if (opensAt != null && !dueAt.isAfter(opensAt)) {
            throw new ValidationFailedException(Map.of("dueAt", "The due date must be after the opening date"));
        }
        if (!Objects.equals(dueAt, savedDueAt) && !dueAt.isAfter(clock.instant())) {
            throw new ValidationFailedException(Map.of("dueAt", "The due date must be in the future"));
        }
    }

    /** 10 uppercase hex characters (40 bits); a collision is very unlikely but still checked. */
    private String newPublicCode() {
        for (int i = 0; i < CODE_ATTEMPTS; i++) {
            byte[] bytes = new byte[5];
            RANDOM.nextBytes(bytes);
            String code = HexFormat.of().withUpperCase().formatHex(bytes);
            if (!assignmentRepository.existsByPublicCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique assignment code");
    }

    private AssignmentResponse toResponse(Assignment assignment) {
        String className = classroomRepository.findById(assignment.getClassId()).map(Classroom::getName).orElse(null);
        Integer questionCount = assignment.getQuizSnapshot() == null ? null
                : snapshots.read(assignment.getQuizSnapshot()).questions().size();
        return mapper.toResponse(assignment, className, questionCount);
    }

    static ApiException assignmentNotFound() {
        return ApiException.notFound("Assignment not found");
    }
}
