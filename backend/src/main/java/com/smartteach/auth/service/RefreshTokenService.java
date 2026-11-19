package com.smartteach.auth.service;

import com.smartteach.auth.config.AuthProperties;
import com.smartteach.auth.entity.RefreshToken;
import com.smartteach.auth.repository.RefreshTokenRepository;
import com.smartteach.common.exception.ApiException;
import com.smartteach.user.entity.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rotating refresh tokens. Each refresh revokes the presented token and issues a new one in the
 * same family. Presenting a token that was already revoked means it was stolen or replayed, so
 * the whole family is revoked and the user has to log in again.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final AuthProperties properties;
    private final Clock clock;

    public record IssuedToken(String value, Instant expiresAt) {
    }

    public record Rotation(User user, IssuedToken token) {
    }

    /** Starts a new family, used on login and register. */
    @Transactional
    public IssuedToken issue(User user) {
        return create(user, UUID.randomUUID());
    }

    // noRollbackFor: the family revocation must be committed even though we answer 401.
    @Transactional(noRollbackFor = ApiException.class)
    public Rotation rotate(String rawToken) {
        Instant now = clock.instant();
        RefreshToken current = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(RefreshTokenService::invalid);

        if (current.isRevoked()) {
            log.warn("Refresh token reuse detected, revoking family {}", current.getFamilyId());
            repository.revokeFamily(current.getFamilyId(), now);
            throw invalid();
        }
        if (current.isExpired(now)) {
            throw invalid();
        }
        // 0 rows means another request rotated this token a moment ago: treat it as reuse too.
        if (repository.revoke(current.getId(), now) == 0) {
            repository.revokeFamily(current.getFamilyId(), now);
            throw invalid();
        }
        User user = current.getUser();
        return new Rotation(user, create(user, current.getFamilyId()));
    }

    /** Logout: revoke the chain the token belongs to. Unknown tokens are ignored. */
    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    private IssuedToken create(User user, UUID familyId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.refreshTokenTtl());
        repository.save(new RefreshToken(user, hash(raw), familyId, now, expiresAt));
        return new IssuedToken(raw, expiresAt);
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static ApiException invalid() {
        return ApiException.unauthorized("Invalid refresh token");
    }
}
