package com.smartteach.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartteach.auth.dto.LoginRequest;
import com.smartteach.auth.dto.RegisterRequest;
import com.smartteach.common.exception.ApiException;
import com.smartteach.user.dto.UserResponse;
import com.smartteach.user.entity.Role;
import com.smartteach.user.entity.User;
import com.smartteach.user.mapper.UserMapper;
import com.smartteach.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final TokenService tokenService = mock(TokenService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private AuthService service;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "bcrypt:" + inv.getArgument(0));
        when(tokenService.createAccessToken(any())).thenReturn("access");
        when(tokenService.accessTokenTtlSeconds()).thenReturn(900L);
        when(refreshTokenService.issue(any())).thenReturn(new RefreshTokenService.IssuedToken("refresh", NOW));
        when(userMapper.toResponse(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return new UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getRole());
        });
        service = new AuthService(userRepository, passwordEncoder, tokenService, refreshTokenService, userMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void registerNormalisesEmailAndAlwaysCreatesTeacher() {
        AuthService.AuthResult result = service.register(
                new RegisterRequest("  Teacher@Example.COM ", "Secret123!", "  Cô Lan  "));

        assertThat(result.response().user().email()).isEqualTo("teacher@example.com");
        assertThat(result.response().user().fullName()).isEqualTo("Cô Lan");
        assertThat(result.response().user().role()).isEqualTo(Role.TEACHER);
        assertThat(result.response().accessToken()).isEqualTo("access");
        assertThat(result.refreshToken().value()).isEqualTo("refresh");
        verify(userRepository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(
                u -> u.getPasswordHash().equals("bcrypt:Secret123!")));
    }

    @Test
    void registerWithTakenEmailIsConflict() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertStatus(() -> service.register(new RegisterRequest("taken@example.com", "Secret123!", "A")),
                HttpStatus.CONFLICT);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerRejectsPasswordLongerThan72Bytes() {
        // 40 Vietnamese characters fit @Size(max = 72) but take 80+ bytes in UTF-8.
        String password = "ệ".repeat(40);

        assertStatus(() -> service.register(new RegisterRequest("a@example.com", password, "A")),
                HttpStatus.BAD_REQUEST);
    }

    @Test
    void loginWithWrongPasswordIsUnauthorized() {
        User user = new User("t@example.com", "stored-hash", "T", Role.TEACHER, NOW);
        when(userRepository.findByEmail("t@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

        assertStatus(() -> service.login(new LoginRequest("t@example.com", "wrong")), HttpStatus.UNAUTHORIZED);
        verify(refreshTokenService, never()).issue(any());
    }

    @Test
    void loginWithUnknownEmailStillChecksAPasswordHash() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertStatus(() -> service.login(new LoginRequest("nobody@example.com", "x")), HttpStatus.UNAUTHORIZED);
        // Compared against the dummy hash so the response time does not reveal which emails exist.
        verify(passwordEncoder).matches(eq("x"), anyString());
    }

    @Test
    void loginWithCorrectPasswordIssuesTokens() {
        User user = new User("t@example.com", "stored-hash", "T", Role.TEACHER, NOW);
        when(userRepository.findByEmail("t@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret123!", "stored-hash")).thenReturn(true);

        AuthService.AuthResult result = service.login(new LoginRequest("T@example.com", "Secret123!"));

        assertThat(result.response().accessToken()).isEqualTo("access");
        verify(refreshTokenService).issue(user);
    }

    @Test
    void refreshWithoutCookieIsUnauthorized() {
        assertStatus(() -> service.refresh(null), HttpStatus.UNAUTHORIZED);
        assertStatus(() -> service.refresh(" "), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutWithoutCookieDoesNothing() {
        service.logout(null);
        verify(refreshTokenService, never()).revoke(any());
    }

    private static void assertStatus(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call)
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(status);
    }
}
