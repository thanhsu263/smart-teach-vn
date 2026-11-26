package com.smartteach.auth.service;

import com.smartteach.auth.entity.PasswordResetToken;
import com.smartteach.auth.repository.PasswordResetTokenRepository;
import com.smartteach.auth.repository.RefreshTokenRepository;
import com.smartteach.common.exception.ApiException;
import com.smartteach.common.mail.EmailSender;
import com.smartteach.common.mail.MailProperties;
import com.smartteach.common.security.Hashing;
import com.smartteach.user.entity.User;
import com.smartteach.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Forgot password" by email. The request always looks the same from outside (204, mail sent in
 * the background), so it cannot be used to find out which emails have an account. The link holds
 * a random token that is valid for 30 minutes and works once.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PasswordResetService {

    static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final MailProperties mailProperties;
    private final Clock clock;

    /** Unknown emails are silently ignored. */
    public void requestReset(String email) {
        userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT)).ifPresent(this::sendLink);
    }

    /** Sets the new password and signs the user out everywhere (all refresh tokens revoked). */
    public void resetPassword(String rawToken, String newPassword) {
        if (newPassword.getBytes(StandardCharsets.UTF_8).length > AuthService.MAX_PASSWORD_BYTES) {
            throw ApiException.badRequest("Password must be at most " + AuthService.MAX_PASSWORD_BYTES + " bytes");
        }
        Instant now = clock.instant();
        PasswordResetToken token = tokenRepository.findByTokenHash(Hashing.sha256Hex(rawToken))
                .filter(t -> t.isUsable(now))
                .orElseThrow(PasswordResetService::invalidLink);
        // 0 rows: the same link was used a moment ago by another request.
        if (tokenRepository.markUsed(token.getId(), now) == 0) {
            throw invalidLink();
        }
        User user = token.getUser();
        tokenRepository.invalidateAllForUser(user.getId(), now);
        refreshTokenRepository.revokeAllForUser(user.getId(), now);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private void sendLink(User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        tokenRepository.save(new PasswordResetToken(user, Hashing.sha256Hex(raw), now, now.plus(TOKEN_TTL)));

        // In the URL fragment, which browsers never send to a server, so it stays out of access logs.
        String link = mailProperties.linkBaseUrl() + "/reset-password#token=" + raw;
        emailSender.send(user.getEmail(), "Đặt lại mật khẩu SmartTeach", """
                Xin chào %s,

                Bạn (hoặc ai đó) vừa yêu cầu đặt lại mật khẩu SmartTeach.
                Mở liên kết sau trong %d phút để đặt mật khẩu mới:

                %s

                Nếu không phải bạn, hãy bỏ qua email này; mật khẩu sẽ không đổi.
                """.formatted(user.getFullName(), TOKEN_TTL.toMinutes(), link));
    }

    private static ApiException invalidLink() {
        return ApiException.badRequest("The reset link is invalid or has expired");
    }
}
