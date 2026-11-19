package com.smartteach.classroom.controller;

import com.smartteach.classroom.dto.AddStudentsRequest;
import com.smartteach.classroom.dto.ClassRequest;
import com.smartteach.classroom.dto.ClassResponse;
import com.smartteach.classroom.dto.StudentCodeResponse;
import com.smartteach.classroom.dto.StudentResponse;
import com.smartteach.classroom.service.ClassroomService;
import com.smartteach.classroom.service.StudentService;
import com.smartteach.common.dto.PageResponse;
import com.smartteach.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/classes")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Classes")
public class ClassroomController {

    private final ClassroomService classroomService;
    private final StudentService studentService;

    @GetMapping
    @Operation(summary = "List my classes, newest first (sort parameters are ignored)")
    public PageResponse<ClassResponse> list(@AuthenticationPrincipal Jwt jwt,
                                            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return classroomService.list(CurrentUser.id(jwt), pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a class (at most 20 per teacher)")
    public ClassResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ClassRequest request) {
        return classroomService.create(CurrentUser.id(jwt), request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one of my classes (404 if it is not mine)")
    public ClassResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return classroomService.get(CurrentUser.id(jwt), id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Rename a class")
    public ClassResponse rename(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                @Valid @RequestBody ClassRequest request) {
        return classroomService.rename(CurrentUser.id(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a class with its students, assignments and results")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        classroomService.delete(CurrentUser.id(jwt), id);
    }

    @GetMapping("/{id}/students")
    @Operation(summary = "Students of a class in order (at most 100, so not paged)")
    public List<StudentResponse> students(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return studentService.list(CurrentUser.id(jwt), id);
    }

    @PostMapping("/{id}/students")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add several students; the response is the only time their access codes are shown")
    public List<StudentCodeResponse> addStudents(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                                 @Valid @RequestBody AddStudentsRequest request) {
        return studentService.addAll(CurrentUser.id(jwt), id, request);
    }
}
