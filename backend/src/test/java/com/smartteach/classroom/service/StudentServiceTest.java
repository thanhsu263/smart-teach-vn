package com.smartteach.classroom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartteach.classroom.dto.AddStudentsRequest;
import com.smartteach.classroom.dto.StudentCodeResponse;
import com.smartteach.classroom.entity.Classroom;
import com.smartteach.classroom.entity.Student;
import com.smartteach.classroom.mapper.ClassroomMapperImpl;
import com.smartteach.classroom.repository.ClassroomRepository;
import com.smartteach.classroom.repository.StudentRepository;
import com.smartteach.common.exception.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-20T08:00:00Z");
    private static final long OWNER = 7L;
    private static final long CLASS_ID = 3L;

    @Mock
    ClassroomRepository classroomRepository;

    @Mock
    StudentRepository studentRepository;

    private StudentService service;
    private final Classroom classroom = new Classroom(OWNER, "10A1", NOW);

    @BeforeEach
    void setUp() {
        service = new StudentService(classroomRepository, studentRepository, new ClassroomMapperImpl(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void addAllAppendsAfterTheLastPositionWithFreshCodes() {
        when(classroomRepository.findByIdAndOwnerIdForUpdate(CLASS_ID, OWNER)).thenReturn(Optional.of(classroom));
        when(studentRepository.countByClassroomId(CLASS_ID)).thenReturn(2L);
        when(studentRepository.findMaxPosition(CLASS_ID)).thenReturn(4);

        List<StudentCodeResponse> result = service.addAll(OWNER, CLASS_ID,
                new AddStudentsRequest(List.of(" An ", "Bình")));

        assertThat(result).extracting(StudentCodeResponse::name).containsExactly("An", "Bình");
        assertThat(result).extracting(StudentCodeResponse::position).containsExactly(5, 6);
        assertThat(result.getFirst().accessCode()).isNotEqualTo(result.getLast().accessCode());
        assertThat(result.getFirst().codeHint()).isEqualTo(AccessCodes.hint(result.getFirst().accessCode()));
        verify(studentRepository).saveAll(anyList());
    }

    @Test
    void addAllRejectsGoingOverTheLimit() {
        when(classroomRepository.findByIdAndOwnerIdForUpdate(CLASS_ID, OWNER)).thenReturn(Optional.of(classroom));
        when(studentRepository.countByClassroomId(CLASS_ID)).thenReturn(99L);

        assertThatThrownBy(() -> service.addAll(OWNER, CLASS_ID, new AddStudentsRequest(List.of("A", "B"))))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(studentRepository, never()).saveAll(anyList());
    }

    @Test
    void addAllToAnotherTeachersClassIsNotFound() {
        when(classroomRepository.findByIdAndOwnerIdForUpdate(CLASS_ID, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addAll(OWNER, CLASS_ID, new AddStudentsRequest(List.of("A"))))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void regenerateCodeChangesTheStoredHash() {
        Student student = new Student(classroom, "An", 0, NOW);
        student.setAccessCodeHash("old");
        student.setCodeHint("OLD1");
        when(studentRepository.findOwned(9L, OWNER)).thenReturn(Optional.of(student));

        StudentCodeResponse response = service.regenerateCode(OWNER, 9L);

        assertThat(student.getAccessCodeHash()).isEqualTo(AccessCodes.hash(response.accessCode()));
        assertThat(student.getCodeHint()).isEqualTo(response.codeHint());
    }

    @Test
    void deleteRemovesAttemptsBeforeTheStudent() {
        Student student = new Student(classroom, "An", 0, NOW);
        when(studentRepository.findOwned(9L, OWNER)).thenReturn(Optional.of(student));

        service.delete(OWNER, 9L);

        InOrder order = inOrder(studentRepository);
        order.verify(studentRepository).deleteAttemptsOf(9L);
        order.verify(studentRepository).delete(student);
    }

    @Test
    void studentOfAnotherTeacherIsNotFound() {
        when(studentRepository.findOwned(9L, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.regenerateCode(OWNER, 9L)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.delete(OWNER, 9L)).isInstanceOf(ApiException.class);
        verify(studentRepository, never()).deleteAttemptsOf(any());
    }
}
