package com.smartteach.common.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Allowed frontend origins, e.g. {@code CORS_ALLOWED_ORIGINS=https://a.app,https://b.app}. */
@ConfigurationProperties("app.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
