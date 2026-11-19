package com.smartteach.quiz.controller;

import com.smartteach.common.dto.PageResponse;
import com.smartteach.common.security.CurrentUser;
import com.smartteach.quiz.dto.QuizRequest;
import com.smartteach.quiz.dto.QuizResponse;
import com.smartteach.quiz.dto.QuizSummaryResponse;
import com.smartteach.quiz.service.QuizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Quizzes")
public class QuizController {

    private final QuizService quizService;

    @GetMapping
    @Operation(summary = "List my quizzes with question counts, most recently updated first")
    public PageResponse<QuizSummaryResponse> list(@AuthenticationPrincipal Jwt jwt,
                                                  @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return quizService.list(CurrentUser.id(jwt), pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a quiz with its questions (at most 50 quizzes per teacher)")
    public QuizResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody QuizRequest request) {
        return quizService.create(CurrentUser.id(jwt), request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a quiz with all questions, options and accepted answers")
    public QuizResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return quizService.get(CurrentUser.id(jwt), id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Save the whole quiz: the questions in the body replace the old ones")
    public QuizResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                               @Valid @RequestBody QuizRequest request) {
        return quizService.update(CurrentUser.id(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a quiz (assignments made from it keep their own copy)")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        quizService.delete(CurrentUser.id(jwt), id);
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Copy a quiz; the copy's title ends with \"(bản sao)\"")
    public QuizResponse duplicate(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return quizService.duplicate(CurrentUser.id(jwt), id);
    }
}
