package com.smartteach.assignment.controller;

import com.smartteach.assignment.dto.AnswerRequest;
import com.smartteach.assignment.dto.AttemptResultResponse;
import com.smartteach.assignment.dto.AttemptView;
import com.smartteach.assignment.dto.SavedAnswerResponse;
import com.smartteach.assignment.dto.StartAttemptRequest;
import com.smartteach.assignment.dto.StartAttemptResponse;
import com.smartteach.assignment.service.AttemptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoints for students (no login). The attempt token travels in its own header rather
 * than {@code Authorization: Bearer}, which the security filter would try to read as a teacher JWT.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Tag(name = "Student attempts (public)")
public class AttemptController {

    static final String TOKEN_HEADER = "X-Attempt-Token";

    private final AttemptService attemptService;

    @PostMapping("/assignments/{code}/start")
    @Operation(summary = "Start (or continue) an attempt with the student access code; returns the attempt token")
    public StartAttemptResponse start(@PathVariable String code, @Valid @RequestBody StartAttemptRequest request) {
        return attemptService.start(code, request.accessCode());
    }

    @GetMapping("/attempts/{id}")
    @Operation(summary = "Questions (without correct answers) and the answers saved so far")
    public AttemptView view(@PathVariable Long id, @RequestHeader(name = TOKEN_HEADER, required = false) String token) {
        return attemptService.view(id, token);
    }

    @PutMapping("/attempts/{id}/answers/{position}")
    @Operation(summary = "Save (autosave) the answer to one question; null clears it")
    public SavedAnswerResponse saveAnswer(@PathVariable Long id, @PathVariable int position,
                                          @RequestHeader(name = TOKEN_HEADER, required = false) String token,
                                          @RequestBody AnswerRequest request) {
        return attemptService.saveAnswer(id, token, position, request.answer());
    }

    @PostMapping("/attempts/{id}/submit")
    @Operation(summary = "Submit and grade the attempt")
    public AttemptResultResponse submit(@PathVariable Long id,
                                        @RequestHeader(name = TOKEN_HEADER, required = false) String token) {
        return attemptService.submit(id, token);
    }

    @GetMapping("/attempts/{id}/result")
    @Operation(summary = "Score and correct answers, depending on the assignment reveal policy")
    public AttemptResultResponse result(@PathVariable Long id,
                                        @RequestHeader(name = TOKEN_HEADER, required = false) String token) {
        return attemptService.result(id, token);
    }
}
