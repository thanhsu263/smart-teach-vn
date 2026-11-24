package com.smartteach.admin.dto;

import java.time.Instant;

/**
 * @param targetUrl  base URL that is pinged; null when not configured (local dev)
 * @param lastPingOk null until the first ping since the backend started
 */
public record KeepAliveStatus(
        boolean enabled,
        String targetUrl,
        int intervalMinutes,
        Instant lastPingAt,
        Boolean lastPingOk) {
}
