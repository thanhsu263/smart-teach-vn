package com.smartteach.common.ratelimit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * In-memory rate limiting, one token bucket per (rule, key). Fine for a single instance;
 * several instances would need a shared store such as Redis.
 */
@Component
public class RateLimiter {

    private final RateLimitProperties properties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(RateLimitProperties properties, Clock clock, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    /** Throws {@link RateLimitedException} when {@code key} has used up its limit for {@code rule}. */
    public void check(RateLimitRule rule, String key) {
        if (!properties.enabled()) {
            return;
        }
        Instant now = clock.instant();
        RateLimitProperties.Limit limit = properties.limit(rule);
        String bucketKey = rule + ":" + key.toLowerCase(Locale.ROOT);
        Duration wait = buckets
                .computeIfAbsent(bucketKey, k -> new TokenBucket(limit.capacity(), limit.period(), now))
                .tryConsume(now);
        if (!wait.isZero()) {
            rejected(rule).increment();
            // Round up so the client never retries a moment too early.
            throw new RateLimitedException(Math.max(1, (wait.toMillis() + 999) / 1000));
        }
    }

    /** Full buckets carry no state, so dropping them keeps memory flat. */
    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    void evictFullBuckets() {
        Instant now = clock.instant();
        buckets.values().removeIf(bucket -> bucket.isFull(now));
    }

    int bucketCount() {
        return buckets.size();
    }

    private Counter rejected(RateLimitRule rule) {
        return Counter.builder("smartteach.ratelimit.rejected")
                .description("Requests refused with 429")
                .tag("rule", rule.name().toLowerCase(Locale.ROOT))
                .register(meterRegistry);
    }
}
