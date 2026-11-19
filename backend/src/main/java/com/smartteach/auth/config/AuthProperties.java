package com.smartteach.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under {@code app.auth}; secrets come from environment variables. */
@ConfigurationProperties("app.auth")
public record AuthProperties(
        String jwtSecret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Cookie cookie) {

    public record Cookie(String name, String path, boolean secure, String sameSite) {
    }
}
