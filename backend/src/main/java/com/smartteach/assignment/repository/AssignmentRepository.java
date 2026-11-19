package com.smartteach.assignment.repository;

import com.smartteach.assignment.dto.AssignmentSummaryResponse;
import com.smartteach.assignment.entity.Assignment;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    Optional<Assignment> findByIdAndOwnerId(Long id, Long ownerId);

    /** Row lock, so publish / close / edit of the same assignment run one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Assignment a where a.id = :id and a.ownerId = :ownerId")
    Optional<Assignment> findByIdAndOwnerIdForUpdate(Long id, Long ownerId);

    Optional<Assignment> findByPublicCode(String publicCode);

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
