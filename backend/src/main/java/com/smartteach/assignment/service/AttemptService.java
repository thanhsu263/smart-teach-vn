package com.smartteach.assignment.service;

import com.smartteach.assignment.dto.AttemptResultResponse;
import com.smartteach.assignment.dto.AttemptResultResponse.QuestionResult;
import com.smartteach.assignment.dto.AttemptView;
import com.smartteach.assignment.dto.AttemptView.PublicQuestion;
import com.smartteach.assignment.dto.QuizSnapshot;
import com.smartteach.assignment.dto.SavedAnswerResponse;
import com.smartteach.assignment.dto.StartAttemptResponse;
import com.smartteach.assignment.entity.Assignment;
import com.smartteach.assignment.entity.AssignmentStatus;
import com.smartteach.assignment.entity.Attempt;
import com.smartteach.assignment.entity.AttemptAnswer;
import com.smartteach.assignment.entity.AttemptStatus;
import com.smartteach.assignment.entity.RevealPolicy;
import com.smartteach.assignment.repository.AssignmentRepository;
import com.smartteach.assignment.repository.AttemptAnswerRepository;
import com.smartteach.assignment.repository.AttemptRepository;
import com.smartteach.classroom.entity.Student;
import com.smartteach.classroom.repository.StudentRepository;
import com.smartteach.classroom.service.AccessCodes;
import com.smartteach.common.exception.ApiException;
import com.smartteach.common.security.Hashing;
import com.smartteach.quiz.dto.OptionResponse;
import com.smartteach.quiz.dto.QuestionResponse;
import com.smartteach.quiz.service.Grading;
import io.micrometer.core.instrument.MeterRegistry;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * Student side, without an account. A student types the assignment code and their own access
 * code and gets an attempt token; every later call sends that token. Answers are saved one by
 * one (autosave) and only graded on the server when the attempt is submitted.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AttemptService {

    static final Duration TOKEN_TTL = Duration.ofDays(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AssignmentRepository assignmentRepository;
    private final AttemptRepository attemptRepository;
    private final AttemptAnswerRepository answerRepository;
    private final StudentRepository studentRepository;
    private final QuizSnapshots snapshots;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    /**
     * One attempt per student: if they already have one (in progress or submitted) it is reused
     * with a new token, e.g. on another device. A wrong assignment code and a wrong student code
     * give the same 404, so neither can be probed on its own.
     */
    public StartAttemptResponse start(String publicCode, String accessCode) {
        Assignment assignment = assignmentRepository.findByPublicCode(AccessCodes.normalize(publicCode))
                .filter(a -> a.getStatus() != AssignmentStatus.DRAFT)
                .orElseThrow(AttemptService::invalidCodes);
        Student student = studentRepository
                .findFirstByAccessCodeHashAndClassroomId(AccessCodes.hash(accessCode), assignment.getClassId())
                .orElseThrow(AttemptService::invalidCodes);

        Instant now = clock.instant();
        Attempt attempt = attemptRepository
                .findFirstByAssignmentIdAndStudentIdAndStatusNotOrderByStartedAtDescIdDesc(
                        assignment.getId(), student.getId(), AttemptStatus.INVALIDATED)
                .orElse(null);
        if (attempt == null) {
            if (!assignment.isOpen(now)) {
                throw notOpen(assignment, now);
            }
            attempt = attemptRepository.save(new Attempt(assignment.getId(), student.getId(), now));
        }

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        attempt.setTokenHash(Hashing.sha256Hex(token));
        attempt.setTokenExpiresAt(now.plus(TOKEN_TTL));
        return new StartAttemptResponse(token, toView(attempt, assignment, student.getName(), now));
    }

    @Transactional(readOnly = true)
    public AttemptView view(Long attemptId, String token) {
        Attempt attempt = findByToken(attemptId, token);
        Assignment assignment = assignmentOf(attempt);
        return toView(attempt, assignment, studentName(attempt), clock.instant());
    }

    public SavedAnswerResponse saveAnswer(Long attemptId, String token, int position, JsonNode answer) {
        Attempt attempt = findByToken(attemptId, token);
        Assignment assignment = assignmentOf(attempt);
        Instant now = clock.instant();
        checkCanAnswer(attempt, assignment, now);

        List<QuestionResponse> questions = snapshots.read(assignment.getQuizSnapshot()).questions();
        if (position < 0 || position >= questions.size()) {
            throw ApiException.badRequest("There is no question at position " + position);
        }
        AttemptAnswer saved = answerRepository.findByAttemptIdAndPosition(attemptId, position).orElse(null);
        if (answer == null || answer.isNull()) {
            if (saved != null) {
                answerRepository.delete(saved);
            }
            return new SavedAnswerResponse(position, null);
        }
        String problem = Grading.checkShape(questions.get(position), answer);
        if (problem != null) {
            throw ApiException.badRequest(problem);
        }
        String json = snapshots.writeAnswer(answer);
        if (saved == null) {
            saved = answerRepository.save(new AttemptAnswer(attemptId, position, json, now));
        } else {
            saved.setAnswer(json);
            saved.setSavedAt(now);
        }
        return new SavedAnswerResponse(position, saved.getSavedAt());
    }

    /** Grades the saved answers. Submitting twice returns the first result instead of an error. */
    public AttemptResultResponse submit(Long attemptId, String token) {
        Attempt attempt = findByToken(attemptId, token);
        Assignment assignment = assignmentOf(attempt);
        Instant now = clock.instant();
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            checkCanAnswer(attempt, assignment, now);
            grade(attempt, snapshots.read(assignment.getQuizSnapshot()).questions(), now);
            meterRegistry.counter("smartteach.attempts.submitted").increment();
        }
        return toResult(attempt, assignment, now);
    }

    /**
     * Deadline passed or assignment closed: grades every attempt still in progress with the
     * answers saved so far, as if the student had pressed submit at {@code endedAt}.
     */
    public int submitInProgress(Assignment assignment, Instant endedAt) {
        List<Attempt> open = attemptRepository.findByAssignmentIdAndStatus(assignment.getId(), AttemptStatus.IN_PROGRESS);
        if (open.isEmpty()) {
            return 0;
        }
        List<QuestionResponse> questions = snapshots.read(assignment.getQuizSnapshot()).questions();
        for (Attempt attempt : open) {
            grade(attempt, questions, endedAt);
        }
        // The summary mail is built from SQL counts right after this; they must see the grades.
        attemptRepository.flush();
        meterRegistry.counter("smartteach.attempts.auto_submitted").increment(open.size());
        return open.size();
    }

    @Transactional(readOnly = true)
    public AttemptResultResponse result(Long attemptId, String token) {
        Attempt attempt = findByToken(attemptId, token);
        if (attempt.getStatus() != AttemptStatus.SUBMITTED) {
            throw ApiException.conflict("The attempt has not been submitted yet");
        }
        return toResult(attempt, assignmentOf(attempt), clock.instant());
    }

    private void grade(Attempt attempt, List<QuestionResponse> questions, Instant now) {
        int correct = 0;
        for (AttemptAnswer answer : answerRepository.findByAttemptIdOrderByPosition(attempt.getId())) {
            boolean isCorrect = Grading.isCorrect(questions.get(answer.getPosition()),
                    snapshots.readAnswer(answer.getAnswer()));
            answer.setCorrect(isCorrect);
            if (isCorrect) {
                correct++;
            }
        }
        attempt.setStatus(AttemptStatus.SUBMITTED);
        attempt.setSubmittedAt(now);
        attempt.setCorrectCount(correct);
        attempt.setQuestionTotal(questions.size());
        attempt.setScore(Grading.score(correct, questions.size()));
    }

    /**
     * The token must belong to this attempt and still be valid. Any mismatch is a 404 rather than
     * a 401/403, so the response does not tell whether the attempt exists.
     */
    private Attempt findByToken(Long attemptId, String token) {
        if (token == null || token.isBlank()) {
            throw attemptNotFound();
        }
        return attemptRepository.findByTokenHash(Hashing.sha256Hex(token.strip()))
                .filter(a -> a.getId().equals(attemptId))
                .filter(a -> a.getStatus() != AttemptStatus.INVALIDATED)
                .filter(a -> a.getTokenExpiresAt() != null && a.getTokenExpiresAt().isAfter(clock.instant()))
                .orElseThrow(AttemptService::attemptNotFound);
    }

    private Assignment assignmentOf(Attempt attempt) {
        return assignmentRepository.findById(attempt.getAssignmentId()).orElseThrow(AttemptService::attemptNotFound);
    }

    private String studentName(Attempt attempt) {
        return studentRepository.findById(attempt.getStudentId()).map(Student::getName).orElse("");
    }

    private static void checkCanAnswer(Attempt attempt, Assignment assignment, Instant now) {
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw ApiException.conflict("The attempt has already been submitted");
        }
        if (!assignment.isOpen(now)) {
            throw notOpen(assignment, now);
        }
    }

    private AttemptView toView(Attempt attempt, Assignment assignment, String studentName, Instant now) {
        List<PublicQuestion> questions = snapshots.read(assignment.getQuizSnapshot()).questions().stream()
                .map(q -> new PublicQuestion(q.position(), q.kind(), q.prompt(),
                        q.options().stream().map(OptionResponse::label).toList()))
                .toList();
        Map<Integer, JsonNode> answers = new LinkedHashMap<>();
        for (AttemptAnswer answer : answerRepository.findByAttemptIdOrderByPosition(attempt.getId())) {
            answers.put(answer.getPosition(), snapshots.readAnswer(answer.getAnswer()));
        }
        return new AttemptView(attempt.getId(), assignment.getTitle(), assignment.getDescription(), studentName,
                attempt.getStatus(), assignment.isOpen(now), assignment.getDueAt(), assignment.getRevealPolicy(),
                questions, answers);
    }

    /** Score and correct answers are shown right away, or only once the assignment is over. */
    private AttemptResultResponse toResult(Attempt attempt, Assignment assignment, Instant now) {
        boolean revealed = assignment.getRevealPolicy() == RevealPolicy.IMMEDIATE || assignment.isEnded(now);
        List<QuestionResult> questions = new ArrayList<>();
        if (revealed) {
            QuizSnapshot snapshot = snapshots.read(assignment.getQuizSnapshot());
            Map<Integer, AttemptAnswer> answers = answerRepository.findByAttemptIdOrderByPosition(attempt.getId())
                    .stream().collect(Collectors.toMap(AttemptAnswer::getPosition, Function.identity()));
            for (QuestionResponse q : snapshot.questions()) {
                AttemptAnswer answer = answers.get(q.position());
                questions.add(new QuestionResult(q.position(), q.kind(), q.prompt(), q.options(),
                        q.acceptedAnswers(), answer == null ? null : snapshots.readAnswer(answer.getAnswer()),
                        answer != null && Boolean.TRUE.equals(answer.getCorrect())));
            }
        }
        return new AttemptResultResponse(attempt.getId(), assignment.getTitle(), studentName(attempt),
                attempt.getStatus(), attempt.getSubmittedAt(), revealed,
                revealed ? attempt.getScore() : null,
                revealed ? attempt.getCorrectCount() : null,
                revealed ? attempt.getQuestionTotal() : null,
                questions);
    }

    private static ApiException notOpen(Assignment assignment, Instant now) {
        if (assignment.getOpensAt() != null && assignment.getOpensAt().isAfter(now)
                && assignment.getStatus() == AssignmentStatus.PUBLISHED) {
            return ApiException.conflict("The assignment is not open yet");
        }
        return ApiException.conflict("The assignment is closed");
    }

    private static ApiException invalidCodes() {
        return ApiException.notFound("Assignment code or student code is invalid");
    }

    private static ApiException attemptNotFound() {
        return ApiException.notFound("Attempt not found");
    }
}
