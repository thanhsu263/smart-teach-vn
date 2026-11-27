package com.smartteach.assignment.service;

import com.smartteach.assignment.dto.AssignmentResponse;
import com.smartteach.assignment.entity.Assignment;
import com.smartteach.assignment.entity.AssignmentStatus;
import com.smartteach.assignment.repository.AssignmentRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What happens when an assignment ends (teacher closes it, or the deadline passes): attempts left
 * in progress are submitted and graded, and the teacher gets one summary mail. Also the reminder
 * 24 hours before the deadline. Each assignment is handled in its own transaction, under a row
 * lock, so the job and a teacher's close never do the work twice.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AssignmentDeadlineService {

    static final Duration REMINDER_BEFORE = Duration.ofHours(24);

    private final AssignmentService assignmentService;
    private final AssignmentRepository assignmentRepository;
    private final AttemptService attemptService;
    private final AssignmentEmails emails;
    private final Clock clock;

    /** Teacher's close: in the same transaction, so the summary already has every attempt graded. */
    public AssignmentResponse close(Long ownerId, Long assignmentId) {
        AssignmentResponse response = assignmentService.close(ownerId, assignmentId);
        // Already loaded and locked by close(): this is read from the persistence context.
        assignmentRepository.findById(assignmentId).ifPresent(this::finish);
        return response;
    }

    @Transactional(readOnly = true)
    public List<Long> endedWithWorkLeft() {
        return assignmentRepository.findEndedWithWorkLeft(clock.instant());
    }

    @Transactional(readOnly = true)
    public List<Long> dueForReminder() {
        Instant now = clock.instant();
        return assignmentRepository.findDueForReminder(now, now.plus(REMINDER_BEFORE));
    }

    public void finishIfEnded(Long assignmentId) {
        assignmentRepository.findByIdForUpdate(assignmentId)
                .filter(a -> a.isEnded(clock.instant()))
                .ifPresent(this::finish);
    }

    public void remindIfDue(Long assignmentId) {
        Instant now = clock.instant();
        Assignment assignment = assignmentRepository.findByIdForUpdate(assignmentId).orElse(null);
        if (assignment == null || assignment.getStatus() != AssignmentStatus.PUBLISHED
                || assignment.getDueAt() == null || !assignment.getDueAt().isAfter(now)) {
            return;
        }
        // Marked even when no mail goes out (everyone submitted, mails off): checked once only.
        if (assignmentRepository.markReminderQueued(assignmentId, now) == 1) {
            emails.queueReminder(assignment);
        }
    }

    private void finish(Assignment assignment) {
        int autoSubmitted = attemptService.submitInProgress(assignment, assignment.endedAt());
        if (assignmentRepository.markSummaryQueued(assignment.getId(), clock.instant()) == 1) {
            emails.queueSummary(assignment, autoSubmitted);
        }
    }
}
