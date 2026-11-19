package com.smartteach.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartteach.auth.config.AuthProperties;
import com.smartteach.auth.entity.RefreshToken;
import com.smartteach.auth.repository.RefreshTokenRepository;
import com.smartteach.common.exception.ApiException;
import com.smartteach.user.entity.Role;
import com.smartteach.user.entity.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String RAW = "raw-token";

    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final User user = new User("t@example.com", "hash", "Teacher", Role.TEACHER, NOW);
    private final UUID family = UUID.randomUUID();
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties("x".repeat(32), Duration.ofMinutes(15), Duration.ofDays(14),
                new AuthProperties.Cookie("refresh_token", "/api/auth", false, "Lax"));
        service = new RefreshTokenService(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void issueStoresOnlyTheHashAndReturnsRawValue() {
        RefreshTokenService.IssuedToken issued = service.issue(user);

        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
        verify(repository).save(org.mockito.ArgumentMatchers.argThat(t ->
                t.getTokenHash().equals(RefreshTokenService.hash(issued.value()))
                        && !t.getTokenHash().equals(issued.value())));
    }

    @Test
    void rotateRevokesCurrentAndIssuesInSameFamily() {
        RefreshToken current = token(NOW.plus(Duration.ofDays(1)));
        when(repository.findByTokenHash(RefreshTokenService.hash(RAW))).thenReturn(Optional.of(current));
        when(repository.revoke(any(), any())).thenReturn(1);

        RefreshTokenService.Rotation rotation = service.rotate(RAW);

        assertThat(rotation.user()).isSameAs(user);
        assertThat(rotation.token().value()).isNotEqualTo(RAW);
        verify(repository).save(org.mockito.ArgumentMatchers.argThat(t -> t.getFamilyId().equals(family)));
        verify(repository, never()).revokeFamily(any(), any());
    }

    @Test
    void unknownTokenIsUnauthorized() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertUnauthorized(() -> service.rotate(RAW));
    }

    @Test
    void expiredTokenIsUnauthorizedWithoutRevokingFamily() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(token(NOW.minusSeconds(1))));

        assertUnauthorized(() -> service.rotate(RAW));
        verify(repository, never()).revokeFamily(any(), any());
        verify(repository, never()).revoke(anyLong(), any());
    }

    @Test
    void reusedRevokedTokenRevokesWholeFamily() {
        RefreshToken revoked = token(NOW.plus(Duration.ofDays(1)));
        org.springframework.test.util.ReflectionTestUtils.setField(revoked, "revokedAt", NOW.minusSeconds(60));
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(revoked));

        assertUnauthorized(() -> service.rotate(RAW));
        verify(repository).revokeFamily(family, NOW);
        verify(repository, never()).save(any());
    }

    @Test
    void losingAConcurrentRotationIsTreatedAsReuse() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(token(NOW.plus(Duration.ofDays(1)))));
        when(repository.revoke(any(), any())).thenReturn(0);

        assertUnauthorized(() -> service.rotate(RAW));
        verify(repository).revokeFamily(family, NOW);
        verify(repository, never()).save(any());
    }

    @Test
    void revokeRevokesFamilyAndIgnoresUnknownToken() {
        when(repository.findByTokenHash(RefreshTokenService.hash(RAW)))
                .thenReturn(Optional.of(token(NOW.plus(Duration.ofDays(1)))));

        service.revoke(RAW);
        service.revoke("unknown");

        verify(repository).revokeFamily(family, NOW);
    }

    private RefreshToken token(Instant expiresAt) {
        RefreshToken token = new RefreshToken(user, RefreshTokenService.hash(RAW), family, NOW.minusSeconds(3600), expiresAt);
        org.springframework.test.util.ReflectionTestUtils.setField(token, "id", 42L);
        return token;
    }

    private static void assertUnauthorized(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
