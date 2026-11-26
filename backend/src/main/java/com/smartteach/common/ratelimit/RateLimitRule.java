package com.smartteach.common.ratelimit;

/** One limit per endpoint (or group of endpoints); thresholds live in {@code app.rate-limit.rules}. */
public enum RateLimitRule {
    /** Every /api/auth call from one IP. Stops one client from trying many emails. */
    AUTH_IP,
    /** Login attempts for one email from one IP. */
    LOGIN,
    REGISTER,
    REFRESH,
    /** Reset mails per email address, so nobody can flood someone's inbox. */
    FORGOT_PASSWORD,
    RESET_PASSWORD,
    /** Per IP. A whole class often shares one school IP, so these limits are generous. */
    STUDENT_START,
    LIVE_JOIN
}
