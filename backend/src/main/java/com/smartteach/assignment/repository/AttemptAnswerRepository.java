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

    /**
     * Per question position over the submitted attempts: correct answers in total, in the
     * {@code groupSize} best attempts and in the {@code groupSize} worst ones. The window functions
     * number the attempts by score once (best first, ties broken by id so the groups are always
     * exactly {@code groupSize} big); the aggregate then filters on that rank. Aliases are quoted
     * because PostgreSQL lowercases bare ones and the projection getters are camelCase.
     */
    @Query(value = """
            WITH ranked AS (
                SELECT id,
                       ROW_NUMBER() OVER (ORDER BY score DESC, id) AS score_rank,
                       COUNT(*) OVER () AS total
                FROM attempts
                WHERE assignment_id = :assignmentId AND status = 'submitted'
            )
            SELECT aa.position AS "position",
                   COUNT(*) FILTER (WHERE aa.is_correct) AS "correct",
                   COUNT(*) FILTER (WHERE aa.is_correct AND r.score_rank <= :groupSize) AS "upperCorrect",
                   COUNT(*) FILTER (WHERE aa.is_correct AND r.score_rank > r.total - :groupSize) AS "lowerCorrect"
            FROM ranked r
            JOIN attempt_answers aa ON aa.attempt_id = r.id
            GROUP BY aa.position""", nativeQuery = true)
    List<GroupCorrectCounts> countCorrectByScoreGroup(Long assignmentId, int groupSize);

    /** Wrong answers of submitted attempts, oldest first (ties for "most chosen" go to the first seen). */
    @Query("""
            select new com.smartteach.assignment.repository.WrongAnswerRow(aa.position, aa.answer)
            from AttemptAnswer aa join Attempt a on a.id = aa.attemptId
            where a.assignmentId = :assignmentId and a.status = :status and aa.correct = false
            order by aa.id""")
    List<WrongAnswerRow> findWrongAnswers(Long assignmentId, AttemptStatus status);
}
