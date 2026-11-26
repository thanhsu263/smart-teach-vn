package com.smartteach.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    };

    private RateLimiter limiter(boolean enabled) {
        var limit = new RateLimitProperties.Limit(2, Duration.ofMinutes(1));
        var properties = new RateLimitProperties(enabled, Map.of(RateLimitRule.LOGIN, limit));
        return new RateLimiter(properties, clock, meters);
    }

    @Test
    void rejectsOverTheLimitWithRetryAfterAndCountsIt() {
        RateLimiter limiter = limiter(true);
        limiter.check(RateLimitRule.LOGIN, "1.2.3.4|a@x.vn");
        limiter.check(RateLimitRule.LOGIN, "1.2.3.4|a@x.vn");

        assertThatThrownBy(() -> limiter.check(RateLimitRule.LOGIN, "1.2.3.4|a@x.vn"))
                .isInstanceOfSatisfying(RateLimitedException.class,
                        ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(30));
        assertThat(meters.counter("smartteach.ratelimit.rejected", "rule", "login").count()).isEqualTo(1);
    }

    @Test
    void keysAreSeparateAndCaseInsensitive() {
        RateLimiter limiter = limiter(true);
        limiter.check(RateLimitRule.LOGIN, "ip|A@x.vn");
        limiter.check(RateLimitRule.LOGIN, "ip|a@x.vn");

        assertThatThrownBy(() -> limiter.check(RateLimitRule.LOGIN, "ip|a@X.vn"))
                .isInstanceOf(RateLimitedException.class);
        limiter.check(RateLimitRule.LOGIN, "ip|b@x.vn");
    }

    @Test
    void disabledLimiterAllowsEverything() {
        RateLimiter limiter = limiter(false);
        for (int i = 0; i < 10; i++) {
            limiter.check(RateLimitRule.LOGIN, "k");
        }
        assertThat(limiter.bucketCount()).isZero();
    }

    @Test
    void evictsOnlyBucketsThatRefilled() {
        RateLimiter limiter = limiter(true);
        limiter.check(RateLimitRule.LOGIN, "old");
        now = now.plusSeconds(50);
        limiter.check(RateLimitRule.LOGIN, "new");

        now = now.plusSeconds(15);
        limiter.evictFullBuckets();

        assertThat(limiter.bucketCount()).isEqualTo(1);
    }

    @Test
    void missingRuleConfigFailsLoudly() {
        RateLimiter limiter = limiter(true);
        assertThatThrownBy(() -> limiter.check(RateLimitRule.REGISTER, "k"))
                .isInstanceOf(IllegalStateException.class);
    }
}
