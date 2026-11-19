package com.smartteach.classroom.service;

import com.smartteach.classroom.dto.ClassRequest;
import com.smartteach.classroom.dto.ClassResponse;
import com.smartteach.classroom.entity.Classroom;
import com.smartteach.classroom.mapper.ClassroomMapper;
import com.smartteach.classroom.repository.ClassroomRepository;
import com.smartteach.classroom.repository.StudentRepository;
import com.smartteach.common.dto.PageResponse;
import com.smartteach.common.exception.ApiException;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every lookup filters by owner, so another teacher's class is reported as "not found" (404),
 * not "forbidden": the API does not reveal that the id exists.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassroomService {

    static final int MAX_CLASSES_PER_TEACHER = 20;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final ClassroomRepository classroomRepository;
    private final StudentRepository studentRepository;
    private final ClassroomMapper mapper;
    private final Clock clock;

    /** The client picks page and size; the order is fixed (newest first). */
    public PageResponse<ClassResponse> list(Long ownerId, Pageable pageable) {
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
        return PageResponse.from(classroomRepository.findSummaries(ownerId, page));
    }

    public ClassResponse get(Long ownerId, Long classId) {
        Classroom classroom = findOwned(ownerId, classId);
        return mapper.toResponse(classroom, studentRepository.countByClassroomId(classId));
    }

    @Transactional
    public ClassResponse create(Long ownerId, ClassRequest request) {
        // Not locked: only the teacher creates their own classes, so a race could at worst
        // add one class over the limit, which does no harm.
        if (classroomRepository.countByOwnerId(ownerId) >= MAX_CLASSES_PER_TEACHER) {
            throw ApiException.conflict("A teacher can have at most " + MAX_CLASSES_PER_TEACHER + " classes");
        }
        Classroom classroom = classroomRepository.save(
                new Classroom(ownerId, request.name().strip(), clock.instant()));
        return mapper.toResponse(classroom, 0);
    }

    @Transactional
    public ClassResponse rename(Long ownerId, Long classId, ClassRequest request) {
        Classroom classroom = findOwned(ownerId, classId);
        classroom.setName(request.name().strip());
        return mapper.toResponse(classroom, studentRepository.countByClassroomId(classId));
    }

    /** Students, assignments and their attempts are removed by ON DELETE CASCADE in the database. */
    @Transactional
    public void delete(Long ownerId, Long classId) {
        classroomRepository.delete(findOwned(ownerId, classId));
    }

    Classroom findOwned(Long ownerId, Long classId) {
        return classroomRepository.findByIdAndOwnerId(classId, ownerId)
                .orElseThrow(ClassroomService::classNotFound);
    }

    static ApiException classNotFound() {
        return ApiException.notFound("Class not found");
    }
}
