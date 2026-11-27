package com.smartteach.assignment.repository;

import com.smartteach.assignment.entity.Attempt;
import com.smartteach.assignment.entity.AttemptStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    Optional<Attempt> findByTokenHash(String tokenHash);

    /** The student's current attempt: in progress or submitted (invalidated ones are ignored). */
    Optional<Attempt> findFirstByAssignmentIdAndStudentIdAndStatusNotOrderByStartedAtDescIdDesc(
            Long assignmentId, Long studentId, AttemptStatus status);

    List<Attempt> findByAssignmentIdAndStatusNot(Long assignmentId, AttemptStatus status);

    long countByAssignmentIdAndStatus(Long assignmentId, AttemptStatus status);

    /** Answers go with them (ON DELETE CASCADE). */
    @Modifying
    @Query(value = "DELETE FROM attempts WHERE started_at < :cutoff", nativeQuery = true)
    int deleteStartedBefore(Instant cutoff);
}
