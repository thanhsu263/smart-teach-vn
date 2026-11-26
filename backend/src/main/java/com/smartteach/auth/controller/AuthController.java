package com.smartteach.auth.controller;

import com.smartteach.auth.config.AuthProperties;
import com.smartteach.auth.dto.AuthResponse;
import com.smartteach.auth.dto.ForgotPasswordRequest;
import com.smartteach.auth.dto.LoginRequest;
import com.smartteach.auth.dto.RegisterRequest;
import com.smartteach.auth.dto.ResetPasswordRequest;
import com.smartteach.auth.service.AuthService;
import com.smartteach.auth.service.AuthService.AuthResult;
import com.smartteach.auth.service.PasswordResetService;
import com.smartteach.common.ratelimit.RateLimitRule;
import com.smartteach.common.ratelimit.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Access token in the body, refresh token in an HttpOnly cookie")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final AuthProperties properties;
    private final Clock clock;
    private final RateLimiter rateLimiter;

    @PostMapping("/register")
    @Operation(summary = "Create a teacher account and sign in")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        rateLimiter.check(RateLimitRule.AUTH_IP, ip);
        rateLimiter.check(RateLimitRule.REGISTER, ip);
        return withRefreshCookie(HttpStatus.CREATED, authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with email and password")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        rateLimiter.check(RateLimitRule.AUTH_IP, ip);
        // Per IP and email together: a stranger guessing passwords cannot lock the real user out.
        rateLimiter.check(RateLimitRule.LOGIN, ip + "|" + request.email().strip());
        return withRefreshCookie(HttpStatus.OK, authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate the refresh cookie and get a new access token")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        rateLimiter.check(RateLimitRule.REFRESH, request.getRemoteAddr());
        return withRefreshCookie(HttpStatus.OK, authService.refresh(readRefreshCookie(request)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the refresh token and clear the cookie")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(readRefreshCookie(request));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .build();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Email a password reset link",
            description = "Always 204, also for unknown emails, so it does not reveal who has an account.")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                               HttpServletRequest http) {
        rateLimiter.check(RateLimitRule.AUTH_IP, http.getRemoteAddr());
        // Per email too, so nobody can flood one inbox from many addresses.
        rateLimiter.check(RateLimitRule.FORGOT_PASSWORD, request.email().strip());
        passwordResetService.requestReset(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Set a new password with the token from the email",
            description = "The token works once, for 30 minutes. Every session of the user is signed out.")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request,
                                              HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        rateLimiter.check(RateLimitRule.AUTH_IP, ip);
        rateLimiter.check(RateLimitRule.RESET_PASSWORD, ip);
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(HttpStatus status, AuthResult result) {
        Duration maxAge = Duration.between(clock.instant(), result.refreshToken().expiresAt());
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken().value(), maxAge).toString())
                .body(result.response());
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        AuthProperties.Cookie cookie = properties.cookie();
        return ResponseCookie.from(cookie.name(), value)
                .httpOnly(true)
                .secure(cookie.secure())
                .sameSite(cookie.sameSite())
                .path(cookie.path())
                .maxAge(maxAge)
                .build();
    }

    private String readRefreshCookie(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, properties.cookie().name());
        return cookie == null ? null : cookie.getValue();
    }
}
