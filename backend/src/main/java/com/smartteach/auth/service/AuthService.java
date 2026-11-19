package com.smartteach.auth.service;

import com.smartteach.auth.dto.AuthResponse;
import com.smartteach.auth.dto.LoginRequest;
import com.smartteach.auth.dto.RegisterRequest;
import com.smartteach.auth.service.RefreshTokenService.IssuedToken;
import com.smartteach.auth.service.RefreshTokenService.Rotation;
import com.smartteach.common.exception.ApiException;
import com.smartteach.user.entity.Role;
import com.smartteach.user.entity.User;
import com.smartteach.user.mapper.UserMapper;
import com.smartteach.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    /** BCrypt only looks at the first 72 bytes; longer input is rejected instead of silently cut. */
    static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final UserMapper userMapper;
    private final Clock clock;
    /** Compared against when the email is unknown, so both cases take the same time. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, TokenService tokenService,
                       RefreshTokenService refreshTokenService, UserMapper userMapper, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.userMapper = userMapper;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("timing-equaliser-not-a-real-password");
    }

    /** Access token for the response body plus the refresh token for the cookie. */
    public record AuthResult(AuthResponse response, IssuedToken refreshToken) {
    }

    /** Public sign-up always creates a TEACHER; ADMIN accounts are created directly in the database. */
    @Transactional
    public AuthResult register(RegisterRequest request) {
        if (tooLong(request.password())) {
            throw ApiException.badRequest("Password must be at most " + MAX_PASSWORD_BYTES + " bytes");
        }
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("Email is already registered");
        }
        User user = new User(email, passwordEncoder.encode(request.password()),
                request.fullName().trim(), Role.TEACHER, clock.instant());
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two sign-ups with the same email at the same time: the unique index decides.
            throw ApiException.conflict("Email is already registered");
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        Optional<User> user = userRepository.findByEmail(normalizeEmail(request.email()));
        boolean valid = !tooLong(request.password())
                && passwordEncoder.matches(request.password(), user.map(User::getPasswordHash).orElse(dummyHash))
                && user.isPresent();
        if (!valid) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        return issueTokens(user.get());
    }

    // Not @Transactional: rotate() has its own transaction that must commit even when it throws.
    public AuthResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw ApiException.unauthorized("Invalid refresh token");
        }
        Rotation rotation = refreshTokenService.rotate(rawRefreshToken);
        return new AuthResult(accessResponse(rotation.user()), rotation.token());
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revoke(rawRefreshToken);
        }
    }

    private AuthResult issueTokens(User user) {
        return new AuthResult(accessResponse(user), refreshTokenService.issue(user));
    }

    private AuthResponse accessResponse(User user) {
        return new AuthResponse(tokenService.createAccessToken(user), tokenService.accessTokenTtlSeconds(),
                userMapper.toResponse(user));
    }

    private static boolean tooLong(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
