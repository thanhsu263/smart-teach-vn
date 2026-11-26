package com.smartteach.common.ratelimit;

import com.smartteach.common.exception.ApiException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Becomes 429 Too Many Requests with a {@code Retry-After} header. */
@Getter
public class RateLimitedException extends ApiException {

    private final long retryAfterSeconds;

    public RateLimitedException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "Too many requests, try again in " + retryAfterSeconds + " seconds");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
