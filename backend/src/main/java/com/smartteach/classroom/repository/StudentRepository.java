package com.smartteach.classroom.repository;

import com.smartteach.classroom.entity.Student;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface StudentRepository extends JpaRepository<Student, Long> {

    List<Student> findByClassroomIdOrderByPositionAscIdAsc(Long classroomId);

    long countByClassroomId(Long classroomId);

    @Query("select coalesce(max(s.position), -1) from Student s where s.classroom.id = :classroomId")
    int findMaxPosition(Long classroomId);

    /** Finds a student only if their class belongs to the given teacher. */
    @Query("select s from Student s join fetch s.classroom c where s.id = :id and c.ownerId = :ownerId")
    Optional<Student> findOwned(Long id, Long ownerId);

    // attempts.student_id has no ON DELETE CASCADE (SQL Server forbids the second cascade path),
    // so a student's attempts are removed first. attempt_answers cascade from attempts.
    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM attempts WHERE student_id = :studentId", nativeQuery = true)
    void deleteAttemptsOf(Long studentId);
}
