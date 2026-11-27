package com.smartteach.common.mail;

public enum OutboxStatus {
    PENDING,
    SENT,
    /** Gave up after {@link OutboxSender#MAX_ATTEMPTS} tries. */
    FAILED
}
