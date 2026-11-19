package com.smartteach.classroom.service;

import com.smartteach.classroom.dto.AddStudentsRequest;
import com.smartteach.classroom.dto.StudentCodeResponse;
import com.smartteach.classroom.dto.StudentRequest;
import com.smartteach.classroom.dto.StudentResponse;
import com.smartteach.classroom.entity.Classroom;
import com.smartteach.classroom.entity.Student;
import com.smartteach.classroom.mapper.ClassroomMapper;
import com.smartteach.classroom.repository.ClassroomRepository;
import com.smartteach.classroom.repository.StudentRepository;
import com.smartteach.common.exception.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentService {

    static final int MAX_STUDENTS_PER_CLASS = 100;

    private final ClassroomRepository classroomRepository;
    private final StudentRepository studentRepository;
    private final ClassroomMapper mapper;
    private final Clock clock;

    public List<StudentResponse> list(Long ownerId, Long classId) {
        classroomRepository.findByIdAndOwnerId(classId, ownerId).orElseThrow(ClassroomService::classNotFound);
        return studentRepository.findByClassroomIdOrderByPositionAscIdAsc(classId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    /**
     * Adds students at the end of the list and returns each new access code once. The class row
     * is locked, so the limit check and the positions stay correct under parallel requests.
     */
    @Transactional
    public List<StudentCodeResponse> addAll(Long ownerId, Long classId, AddStudentsRequest request) {
        Classroom classroom = classroomRepository.findByIdAndOwnerIdForUpdate(classId, ownerId)
                .orElseThrow(ClassroomService::classNotFound);

        long existing = studentRepository.countByClassroomId(classId);
        if (existing + request.names().size() > MAX_STUDENTS_PER_CLASS) {
            throw ApiException.conflict("A class can have at most " + MAX_STUDENTS_PER_CLASS
                    + " students (it has " + existing + ")");
        }

        int position = studentRepository.findMaxPosition(classId);
        Instant now = clock.instant();
        List<Student> students = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        for (String name : request.names()) {
            Student student = new Student(classroom, name.strip(), ++position, now);
            codes.add(assignNewCode(student));
            students.add(student);
        }
        studentRepository.saveAll(students);

        List<StudentCodeResponse> result = new ArrayList<>();
        for (int i = 0; i < students.size(); i++) {
            result.add(mapper.toCodeResponse(students.get(i), codes.get(i)));
        }
        return result;
    }

    @Transactional
    public StudentResponse rename(Long ownerId, Long studentId, StudentRequest request) {
        Student student = findOwned(ownerId, studentId);
        student.setName(request.name().strip());
        return mapper.toResponse(student);
    }

    @Transactional
    public void delete(Long ownerId, Long studentId) {
        Student student = findOwned(ownerId, studentId);
        studentRepository.deleteAttemptsOf(studentId);
        studentRepository.delete(student);
    }

    /** Replaces the code (e.g. a student forgot it). The old code stops working immediately. */
    @Transactional
    public StudentCodeResponse regenerateCode(Long ownerId, Long studentId) {
        Student student = findOwned(ownerId, studentId);
        String code = assignNewCode(student);
        return mapper.toCodeResponse(student, code);
    }

    private Student findOwned(Long ownerId, Long studentId) {
        return studentRepository.findOwned(studentId, ownerId)
                .orElseThrow(() -> ApiException.notFound("Student not found"));
    }

    private static String assignNewCode(Student student) {
        String code = AccessCodes.generate();
        student.setAccessCodeHash(AccessCodes.hash(code));
        student.setCodeHint(AccessCodes.hint(code));
        return code;
    }
}
