package com.smartteach.assignment.controller;

import com.smartteach.assignment.dto.AnalyticsResponse;
import com.smartteach.assignment.dto.AssignmentRequest;
import com.smartteach.assignment.dto.AssignmentResponse;
import com.smartteach.assignment.dto.AssignmentSummaryResponse;
import com.smartteach.assignment.dto.QuestionStatsResponse;
import com.smartteach.assignment.service.AnalyticsService;
import com.smartteach.assignment.service.AssignmentService;
import com.smartteach.assignment.service.QuestionStatsService;
import com.smartteach.common.dto.PageResponse;
import com.smartteach.common.security.CurrentUser;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Assignments")
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final AnalyticsService analyticsService;
    private final QuestionStatsService questionStatsService;

    @GetMapping
    @Operation(summary = "List my assignments, newest first, optionally only one class")
    public PageResponse<AssignmentSummaryResponse> list(@AuthenticationPrincipal Jwt jwt,
                                                        @RequestParam(required = false) Long classId,
                                                        @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return assignmentService.list(CurrentUser.id(jwt), classId, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a draft assignment of a quiz for a class (at most 100 per teacher)")
    public AssignmentResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AssignmentRequest request) {
        return assignmentService.create(CurrentUser.id(jwt), request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an assignment")
    public AssignmentResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return assignmentService.get(CurrentUser.id(jwt), id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit an assignment. After publishing only title, description, dates and reveal policy change")
    public AssignmentResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                     @Valid @RequestBody AssignmentRequest request) {
        return assignmentService.update(CurrentUser.id(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an assignment and all student attempts")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        assignmentService.delete(CurrentUser.id(jwt), id);
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Publish a draft: the quiz is copied and students can start with the public code")
    public AssignmentResponse publish(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return assignmentService.publish(CurrentUser.id(jwt), id);
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "Close a published assignment: students can no longer answer")
    public AssignmentResponse close(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return assignmentService.close(CurrentUser.id(jwt), id);
    }

    @GetMapping("/{id}/analytics")
    @Operation(summary = "Average score, correct rate per question and progress of every student")
    public AnalyticsResponse analytics(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return analyticsService.analytics(CurrentUser.id(jwt), id);
    }

    @GetMapping("/{id}/question-stats")
    @Operation(summary = "Item analysis per question: correct rate, discrimination, most chosen wrong answer",
            description = "From submitted attempts. Discrimination = (correct in the best 27% - correct in the "
                    + "worst 27%) / group size; null while fewer than 10 attempts are submitted. "
                    + "tooHard: correct rate below 30%; lowDiscrimination: discrimination below 0.2.")
    public QuestionStatsResponse questionStats(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return questionStatsService.questionStats(CurrentUser.id(jwt), id);
    }
}
