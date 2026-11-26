package com.smartteach.common.ratelimit;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code capacity} requests per {@code period}, refilled smoothly (a token bucket). */
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(boolean enabled, Map<RateLimitRule, Limit> rules) {

    public RateLimitProperties {
        rules = rules == null ? Map.of() : Map.copyOf(rules);
    }

    public record Limit(int capacity, Duration period) {
    }

    Limit limit(RateLimitRule rule) {
        Limit limit = rules.get(rule);
        if (limit == null) {
            throw new IllegalStateException("No app.rate-limit.rules entry for " + rule);
        }
        return limit;
    }
}
