package com.smartteach.admin.service;

import com.smartteach.admin.config.KeepAliveProperties;
import com.smartteach.admin.dto.KeepAliveStatus;
import com.smartteach.admin.entity.SystemSettings;
import com.smartteach.admin.repository.SystemSettingsRepository;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin switch for the keep-alive pinger. The flag lives in the database so it survives restarts,
 * but is cached here: reading it on every tick would keep the free-plan database awake too.
 */
@Service
@RequiredArgsConstructor
public class KeepAliveService {

    private final SystemSettingsRepository settingsRepository;
    private final KeepAliveProperties properties;
    private final Clock clock;

    private volatile boolean enabled;
    private volatile Instant lastPingAt;
    private volatile Boolean lastPingOk;

    @PostConstruct
    void loadFlag() {
        enabled = settings().isKeepAliveEnabled();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public KeepAliveStatus status() {
        return new KeepAliveStatus(
                enabled,
                properties.configured() ? properties.url() : null,
                properties.intervalMinutes(),
                lastPingAt,
                lastPingOk);
    }

    @Transactional
    public KeepAliveStatus setEnabled(boolean value) {
        SystemSettings settings = settings();
        settings.setKeepAliveEnabled(value);
        settings.setUpdatedAt(clock.instant());
        enabled = value;
        return status();
    }

    public void recordPing(boolean ok) {
        lastPingAt = clock.instant();
        lastPingOk = ok;
    }

    private SystemSettings settings() {
        return settingsRepository.findById(SystemSettings.ID)
                .orElseThrow(() -> new IllegalStateException("system_settings row is missing (V3 migration)"));
    }
}
