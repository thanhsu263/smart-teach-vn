package com.smartteach.assignment.repository;

import com.smartteach.assignment.dto.AssignmentSummaryResponse;
import com.smartteach.assignment.entity.Assignment;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    Optional<Assignment> findByIdAndOwnerId(Long id, Long ownerId);

    /** Row lock, so publish / close / edit of the same assignment run one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Assignment a where a.id = :id and a.ownerId = :ownerId")
    Optional<Assignment> findByIdAndOwnerIdForUpdate(Long id, Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Assignment a where a.id = :id")
    Optional<Assignment> findByIdForUpdate(Long id);

    Optional<Assignment> findByPublicCode(String publicCode);

    // reminder_queued_at and summary_queued_at are not mapped on the entity, on purpose: only these
    // queries touch them, so the background job never changes the version a teacher's edit form holds.

    /** Ended (closed or past due) and still with work left: the summary or attempts in progress. */
    @Query(value = """
            SELECT a.id FROM assignments a
            WHERE (a.status = 'closed' OR (a.status = 'published' AND a.due_at <= :now))
              AND (a.summary_queued_at IS NULL
                   OR EXISTS (SELECT 1 FROM attempts t WHERE t.assignment_id = a.id AND t.status = 'in_progress'))
            ORDER BY a.id""", nativeQuery = true)
    List<Long> findEndedWithWorkLeft(Instant now);

    /**
     * Due in ({@code now}, {@code until}] and no reminder yet. Assignments open for less than a day
     * (a 15-minute test in class) get none: the teacher is there when it ends.
     */
    @Query(value = """
            SELECT a.id FROM assignments a
            WHERE a.status = 'published' AND a.reminder_queued_at IS NULL
              AND a.due_at > :now AND a.due_at <= :until
              AND COALESCE(a.opens_at, a.created_at) < a.due_at - INTERVAL '24 hours'
            ORDER BY a.id""", nativeQuery = true)
    List<Long> findDueForReminder(Instant now, Instant until);

    /** 1 = this call claimed the summary; 0 = it was already queued (by close or another run). */
    @Modifying
    @Query(value = "UPDATE assignments SET summary_queued_at = :now WHERE id = :id AND summary_queued_at IS NULL",
            nativeQuery = true)
    int markSummaryQueued(Long id, Instant now);

    @Modifying
    @Query(value = "UPDATE assignments SET reminder_queued_at = :now WHERE id = :id AND reminder_queued_at IS NULL",
            nativeQuery = true)
    int markReminderQueued(Long id, Instant now);

    @Modifying
    @Query(value = "UPDATE assignments SET reminder_queued_at = NULL, summary_queued_at = NULL WHERE id = :id",
            nativeQuery = true)
    int resetQueuedMails(Long id);

    boolean existsByPublicCode(String publicCode);

    long countByOwnerId(Long ownerId);

    /** {@code classId} null = all of the teacher's classes. The class name comes in the same query. */
    @Query(value = """
            select new com.smartteach.assignment.dto.AssignmentSummaryResponse(
                a.id, a.classId, c.name, a.title, a.publicCode, a.status, a.opensAt, a.dueAt, a.createdAt)
            from Assignment a join Classroom c on c.id = a.classId
            where a.ownerId = :ownerId and (:classId is null or a.classId = :classId)""",
            countQuery = """
            select count(a) from Assignment a
            where a.ownerId = :ownerId and (:classId is null or a.classId = :classId)""")
    Page<AssignmentSummaryResponse> findSummaries(Long ownerId, Long classId, Pageable pageable);
}
