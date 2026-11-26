package com.smartteach.common.ratelimit;

import java.time.Duration;
import java.time.Instant;

/**
 * Holds up to {@code capacity} tokens and gets them back at a steady rate of capacity per period.
 * Each request takes one token; with none left the caller has to wait for the next one.
 */
final class TokenBucket {

    private final int capacity;
    private final double tokensPerNano;
    private double tokens;
    private Instant lastRefill;

    TokenBucket(int capacity, Duration period, Instant now) {
        if (capacity < 1 || period.isNegative() || period.isZero()) {
            throw new IllegalArgumentException("capacity and period must be positive");
        }
        this.capacity = capacity;
        this.tokensPerNano = capacity / (double) period.toNanos();
        this.tokens = capacity;
        this.lastRefill = now;
    }

    /** Takes a token and returns {@link Duration#ZERO}, or returns how long until a token is free. */
    synchronized Duration tryConsume(Instant now) {
        refill(now);
        if (tokens >= 1) {
            tokens -= 1;
            return Duration.ZERO;
        }
        return Duration.ofNanos((long) Math.ceil((1 - tokens) / tokensPerNano));
    }

    /** A full bucket behaves like a new one, so it can be dropped to save memory. */
    synchronized boolean isFull(Instant now) {
        refill(now);
        return tokens >= capacity;
    }

    private void refill(Instant now) {
        long elapsed = Duration.between(lastRefill, now).toNanos();
        if (elapsed > 0) {
            tokens = Math.min(capacity, tokens + elapsed * tokensPerNano);
            lastRefill = now;
        }
    }
}
