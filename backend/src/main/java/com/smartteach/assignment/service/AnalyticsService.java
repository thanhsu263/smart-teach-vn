package com.smartteach.assignment.service;

import com.smartteach.assignment.dto.AnalyticsResponse;
import com.smartteach.assignment.dto.AnalyticsResponse.QuestionStats;
import com.smartteach.assignment.dto.AnalyticsResponse.StudentProgress;
import com.smartteach.assignment.entity.Assignment;
import com.smartteach.assignment.entity.Attempt;
import com.smartteach.assignment.entity.AttemptStatus;
import com.smartteach.assignment.repository.AttemptAnswerRepository;
import com.smartteach.assignment.repository.AttemptRepository;
import com.smartteach.assignment.repository.PositionStats;
import com.smartteach.classroom.entity.Student;
import com.smartteach.classroom.repository.StudentRepository;
import com.smartteach.quiz.dto.QuestionResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Results of one assignment for its teacher: average score, correct rate per question and the
 * progress of every student in the class (so the teacher sees who has not started). Four queries
 * whatever the class size: assignment, students, attempts, per-question counts.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

    static final String NOT_STARTED = "not_started";

    private final AssignmentService assignmentService;
    private final AttemptRepository attemptRepository;
    private final AttemptAnswerRepository answerRepository;
    private final StudentRepository studentRepository;
    private final QuizSnapshots snapshots;

    public AnalyticsResponse analytics(Long ownerId, Long assignmentId) {
        Assignment assignment = assignmentService.findOwned(ownerId, assignmentId);
        List<Student> students = studentRepository.findByClassroomIdOrderByPositionAscIdAsc(assignment.getClassId());
        // At most one non-invalidated attempt per student (see AttemptService.start).
        Map<Long, Attempt> attempts = attemptRepository
                .findByAssignmentIdAndStatusNot(assignmentId, AttemptStatus.INVALIDATED).stream()
                .collect(Collectors.toMap(Attempt::getStudentId, Function.identity(), (a, b) -> a));

        List<StudentProgress> progress = new ArrayList<>();
        int submitted = 0;
        int inProgress = 0;
        long scoreSum = 0;
        for (Student student : students) {
            Attempt attempt = attempts.get(student.getId());
            if (attempt == null) {
                progress.add(new StudentProgress(student.getId(), student.getName(), NOT_STARTED, null, null, null));
                continue;
            }
            if (attempt.getStatus() == AttemptStatus.SUBMITTED) {
                submitted++;
                scoreSum += attempt.getScore();
            } else {
                inProgress++;
            }
            progress.add(new StudentProgress(student.getId(), student.getName(), attempt.getStatus().getValue(),
                    attempt.getScore(), attempt.getCorrectCount(), attempt.getSubmittedAt()));
        }

        Double averageScore = submitted == 0 ? null : round1((double) scoreSum / submitted);
        return new AnalyticsResponse(students.size(), submitted, inProgress, averageScore,
                questionStats(assignment, submitted), progress);
    }

    private List<QuestionStats> questionStats(Assignment assignment, int submitted) {
        if (assignment.getQuizSnapshot() == null) {
            return List.of();  // draft: no questions yet
        }
        Map<Integer, PositionStats> counts = answerRepository
                .countByPosition(assignment.getId(), AttemptStatus.SUBMITTED).stream()
                .collect(Collectors.toMap(PositionStats::position, Function.identity()));
        List<QuestionStats> stats = new ArrayList<>();
        for (QuestionResponse q : snapshots.read(assignment.getQuizSnapshot()).questions()) {
            PositionStats count = counts.get(q.position());
            int answered = count == null ? 0 : (int) count.answered();
            int correct = count == null ? 0 : (int) count.correct();
            // Unanswered questions count as wrong, so the rate is over all submitted attempts.
            Double rate = submitted == 0 ? null : round1(100.0 * correct / submitted);
            stats.add(new QuestionStats(q.position(), q.prompt(), answered, correct, rate));
        }
        return stats;
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
