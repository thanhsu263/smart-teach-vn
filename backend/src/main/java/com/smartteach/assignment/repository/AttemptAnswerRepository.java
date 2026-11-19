package com.smartteach.assignment.repository;

import com.smartteach.assignment.entity.AttemptAnswer;
import com.smartteach.assignment.entity.AttemptStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, Long> {

    List<AttemptAnswer> findByAttemptIdOrderByPosition(Long attemptId);

    Optional<AttemptAnswer> findByAttemptIdAndPosition(Long attemptId, int position);

    /** Per question position: how many submitted attempts answered it, and how many of them correctly. */
    @Query("""
            select new com.smartteach.assignment.repository.PositionStats(
                aa.position, count(aa), sum(case when aa.correct = true then 1 else 0 end))
            from AttemptAnswer aa join Attempt a on a.id = aa.attemptId
            where a.assignmentId = :assignmentId and a.status = :status
            group by aa.position""")
    List<PositionStats> countByPosition(Long assignmentId, AttemptStatus status);
}
