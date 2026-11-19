package com.smartteach.classroom.repository;

import com.smartteach.classroom.dto.ClassResponse;
import com.smartteach.classroom.entity.Classroom;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ClassroomRepository extends JpaRepository<Classroom, Long> {

    Optional<Classroom> findByIdAndOwnerId(Long id, Long ownerId);

    /** Row lock, so two requests adding students to the same class run one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Classroom c where c.id = :id and c.ownerId = :ownerId")
    Optional<Classroom> findByIdAndOwnerIdForUpdate(Long id, Long ownerId);

    long countByOwnerId(Long ownerId);

    /** One query for the list page, student counts included (no N+1). */
    @Query(value = """
            select new com.smartteach.classroom.dto.ClassResponse(c.id, c.name, count(s.id), c.createdAt)
            from Classroom c left join Student s on s.classroom = c
            where c.ownerId = :ownerId
            group by c.id, c.name, c.createdAt""",
            countQuery = "select count(c) from Classroom c where c.ownerId = :ownerId")
    Page<ClassResponse> findSummaries(Long ownerId, Pageable pageable);
}
