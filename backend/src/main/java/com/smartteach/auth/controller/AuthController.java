package com.smartteach.auth.controller;

import com.smartteach.auth.config.AuthProperties;
import com.smartteach.auth.dto.AuthResponse;
import com.smartteach.auth.dto.LoginRequest;
import com.smartteach.auth.dto.RegisterRequest;
import com.smartteach.auth.service.AuthService;
import com.smartteach.auth.service.AuthService.AuthResult;
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
    private final AuthProperties properties;
    private final Clock clock;

    @PostMapping("/register")
    @Operation(summary = "Create a teacher account and sign in")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return withRefreshCookie(HttpStatus.CREATED, authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with email and password")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(HttpStatus.OK, authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate the refresh cookie and get a new access token")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
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
