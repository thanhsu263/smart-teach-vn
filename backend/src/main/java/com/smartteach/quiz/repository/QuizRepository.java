package com.smartteach.quiz.repository;

import com.smartteach.quiz.dto.QuizSummaryResponse;
import com.smartteach.quiz.entity.Quiz;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    Optional<Quiz> findByIdAndOwnerId(Long id, Long ownerId);

    /** Row lock, so two saves of the same quiz replace its questions one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Quiz q where q.id = :id and q.ownerId = :ownerId")
    Optional<Quiz> findByIdAndOwnerIdForUpdate(Long id, Long ownerId);

    long countByOwnerId(Long ownerId);

    @Query(value = """
            select new com.smartteach.quiz.dto.QuizSummaryResponse(q.id, q.title, count(qu.id), q.createdAt, q.updatedAt)
            from Quiz q left join Question qu on qu.quizId = q.id
            where q.ownerId = :ownerId
            group by q.id, q.title, q.createdAt, q.updatedAt""",
            countQuery = "select count(q) from Quiz q where q.ownerId = :ownerId")
    Page<QuizSummaryResponse> findSummaries(Long ownerId, Pageable pageable);

    /**
     * Questions, options and accepted answers go with it (ON DELETE CASCADE). Assignments keep their
     * snapshot of the quiz and only lose the link to it (ON DELETE SET NULL).
     */
    @Modifying
    @Query("delete from Quiz q where q.id = :id and q.ownerId = :ownerId")
    int deleteOwned(Long id, Long ownerId);
}
