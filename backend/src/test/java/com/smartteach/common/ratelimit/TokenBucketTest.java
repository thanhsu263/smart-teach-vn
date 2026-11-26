package com.smartteach.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TokenBucketTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void allowsCapacityRequestsThenAsksToWait() {
        TokenBucket bucket = new TokenBucket(3, Duration.ofMinutes(1), T0);

        assertThat(bucket.tryConsume(T0)).isZero();
        assertThat(bucket.tryConsume(T0)).isZero();
        assertThat(bucket.tryConsume(T0)).isZero();
        // 3 per minute = one token every 20 seconds.
        assertThat(bucket.tryConsume(T0)).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void refillsSmoothlyOverTime() {
        TokenBucket bucket = new TokenBucket(3, Duration.ofMinutes(1), T0);
        for (int i = 0; i < 3; i++) {
            bucket.tryConsume(T0);
        }

        assertThat(bucket.tryConsume(T0.plusSeconds(15))).isEqualTo(Duration.ofSeconds(5));
        assertThat(bucket.tryConsume(T0.plusSeconds(20))).isZero();
        assertThat(bucket.tryConsume(T0.plusSeconds(20))).isPositive();
    }

    @Test
    void neverHoldsMoreThanCapacity() {
        TokenBucket bucket = new TokenBucket(2, Duration.ofMinutes(1), T0);
        Instant later = T0.plus(Duration.ofHours(1));

        assertThat(bucket.tryConsume(later)).isZero();
        assertThat(bucket.tryConsume(later)).isZero();
        assertThat(bucket.tryConsume(later)).isPositive();
    }

    @Test
    void isFullOnlyAfterFullRefill() {
        TokenBucket bucket = new TokenBucket(2, Duration.ofMinutes(1), T0);
        bucket.tryConsume(T0);

        assertThat(bucket.isFull(T0.plusSeconds(10))).isFalse();
        assertThat(bucket.isFull(T0.plusSeconds(30))).isTrue();
    }

    @Test
    void rejectsInvalidSettings() {
        assertThatThrownBy(() -> new TokenBucket(0, Duration.ofMinutes(1), T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBucket(1, Duration.ZERO, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
