package com.smartteach.common.exception;

import com.smartteach.common.logging.RequestIdFilter;
import com.smartteach.common.ratelimit.RateLimitedException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Turns every error into an RFC 9457 ProblemDetail body that also carries the request id. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(problem(ex.getStatus(), ex.getMessage()));
    }

    @ExceptionHandler(RateLimitedException.class)
    ResponseEntity<ProblemDetail> handleRateLimited(RateLimitedException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(problem(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage()));
    }

    /** Two saves of the same row raced and the version check in the UPDATE lost. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(problem(HttpStatus.CONFLICT, ApiException.MODIFIED_ELSEWHERE));
    }

    // Also reached from the security filter chain, see SecurityProblemHandler.
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(problem(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem(HttpStatus.FORBIDDEN, "Access denied"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error"));
    }

    /** Adds an {@code errors} map (field -> message) so the frontend can show a message per input. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(validationProblem(errors));
    }

    @ExceptionHandler(ValidationFailedException.class)
    ResponseEntity<ProblemDetail> handleValidationFailed(ValidationFailedException ex) {
        return ResponseEntity.badRequest().body(validationProblem(ex.getErrors()));
    }

    /** Errors built by the base class (bad JSON, 405, ...) get the request id here. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            withRequestId(problem);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private static ProblemDetail validationProblem(Map<String, String> errors) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        return withRequestId(ProblemDetail.forStatusAndDetail(status, detail));
    }

    private static ProblemDetail withRequestId(ProblemDetail problem) {
        String requestId = RequestIdFilter.current();
        if (requestId != null) {
            problem.setProperty("requestId", requestId);
        }
        return problem;
    }
}
