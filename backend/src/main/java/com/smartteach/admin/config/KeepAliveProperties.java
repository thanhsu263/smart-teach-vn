package com.smartteach.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param url             public base URL to ping, e.g. {@code https://smartteach-api.onrender.com};
 *                        empty (local dev) makes the keep-alive job a no-op
 * @param intervalMinutes must stay below Render's 15-minute idle limit
 */
@ConfigurationProperties("app.keep-alive")
public record KeepAliveProperties(String url, @DefaultValue("10") int intervalMinutes) {

    public KeepAliveProperties {
        url = url == null ? "" : url.strip().replaceAll("/+$", "");
    }

    public boolean configured() {
        return !url.isEmpty();
    }
}
