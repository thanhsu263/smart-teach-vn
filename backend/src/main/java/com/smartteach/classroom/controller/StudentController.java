package com.smartteach.classroom.controller;

import com.smartteach.classroom.dto.StudentCodeResponse;
import com.smartteach.classroom.dto.StudentRequest;
import com.smartteach.classroom.dto.StudentResponse;
import com.smartteach.classroom.service.StudentService;
import com.smartteach.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Students")
public class StudentController {

    private final StudentService studentService;

    @PutMapping("/{id}")
    @Operation(summary = "Rename a student")
    public StudentResponse rename(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                  @Valid @RequestBody StudentRequest request) {
        return studentService.rename(CurrentUser.id(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a student and their results")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        studentService.delete(CurrentUser.id(jwt), id);
    }

    @PostMapping("/{id}/access-code")
    @Operation(summary = "Issue a new access code; the old one stops working. The code is shown only in this response")
    public StudentCodeResponse regenerateCode(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return studentService.regenerateCode(CurrentUser.id(jwt), id);
    }
}
