package com.smartteach.classroom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartteach.classroom.dto.ClassRequest;
import com.smartteach.classroom.dto.ClassResponse;
import com.smartteach.classroom.entity.Classroom;
import com.smartteach.classroom.mapper.ClassroomMapperImpl;
import com.smartteach.classroom.repository.ClassroomRepository;
import com.smartteach.classroom.repository.StudentRepository;
import com.smartteach.common.exception.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ClassroomServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-20T08:00:00Z");
    private static final long OWNER = 7L;

    @Mock
    ClassroomRepository classroomRepository;

    @Mock
    StudentRepository studentRepository;

    private ClassroomService service;

    @BeforeEach
    void setUp() {
        service = new ClassroomService(classroomRepository, studentRepository, new ClassroomMapperImpl(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createTrimsNameAndSetsOwner() {
        when(classroomRepository.countByOwnerId(OWNER)).thenReturn(0L);
        when(classroomRepository.save(any(Classroom.class))).thenAnswer(inv -> inv.getArgument(0));

        ClassResponse response = service.create(OWNER, new ClassRequest("  10A1 "));

        assertThat(response.name()).isEqualTo("10A1");
        assertThat(response.studentCount()).isZero();
        assertThat(response.createdAt()).isEqualTo(NOW);
    }

    @Test
    void createIsRejectedAtTheLimit() {
        when(classroomRepository.countByOwnerId(OWNER)).thenReturn((long) ClassroomService.MAX_CLASSES_PER_TEACHER);

        assertThatThrownBy(() -> service.create(OWNER, new ClassRequest("10A1")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(classroomRepository, never()).save(any());
    }

    @Test
    void classOfAnotherTeacherIsNotFound() {
        when(classroomRepository.findByIdAndOwnerId(1L, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(OWNER, 1L))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThatThrownBy(() -> service.delete(OWNER, 1L)).isInstanceOf(ApiException.class);
        verify(classroomRepository, never()).delete(any());
    }

    @Test
    void renameUpdatesTheLoadedEntity() {
        Classroom classroom = new Classroom(OWNER, "Old", NOW);
        when(classroomRepository.findByIdAndOwnerId(1L, OWNER)).thenReturn(Optional.of(classroom));
        when(studentRepository.countByClassroomId(1L)).thenReturn(3L);

        ClassResponse response = service.rename(OWNER, 1L, new ClassRequest(" New "));

        assertThat(classroom.getName()).isEqualTo("New");
        assertThat(response.studentCount()).isEqualTo(3);
    }
}
