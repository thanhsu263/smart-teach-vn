package com.smartteach.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.smartteach.admin.config.KeepAliveProperties;
import com.smartteach.admin.dto.KeepAliveStatus;
import com.smartteach.admin.entity.SystemSettings;
import com.smartteach.admin.repository.SystemSettingsRepository;
import com.smartteach.admin.service.KeepAliveService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KeepAliveServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-24T08:00:00Z");

    @Mock
    SystemSettingsRepository repository;

    private KeepAliveService service(String url) {
        return new KeepAliveService(repository, new KeepAliveProperties(url, 10), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void urlIsTrimmedAndBlankMeansNotConfigured() {
        assertThat(new KeepAliveProperties(" https://api.example.com/ ", 10).url()).isEqualTo("https://api.example.com");
        assertThat(new KeepAliveProperties("  ", 10).configured()).isFalse();
        assertThat(new KeepAliveProperties(null, 10).configured()).isFalse();
    }

    @Test
    void setEnabledSavesTheFlagAndCachesIt() {
        SystemSettings row = new SystemSettings(false, Instant.EPOCH);
        when(repository.findById(SystemSettings.ID)).thenReturn(Optional.of(row));
        KeepAliveService service = service("https://api.example.com");

        KeepAliveStatus status = service.setEnabled(true);

        assertThat(service.isEnabled()).isTrue();
        assertThat(row.isKeepAliveEnabled()).isTrue();
        assertThat(row.getUpdatedAt()).isEqualTo(NOW);
        assertThat(status.enabled()).isTrue();
        assertThat(status.targetUrl()).isEqualTo("https://api.example.com");
        assertThat(status.intervalMinutes()).isEqualTo(10);
    }

    @Test
    void statusHasNoUrlWhenNotConfiguredAndNoPingYet() {
        KeepAliveStatus status = service("").status();

        assertThat(status.targetUrl()).isNull();
        assertThat(status.lastPingAt()).isNull();
        assertThat(status.lastPingOk()).isNull();
    }

    @Test
    void recordPingKeepsTheLastResult() {
        KeepAliveService service = service("https://api.example.com");

        service.recordPing(false);

        assertThat(service.status().lastPingAt()).isEqualTo(NOW);
        assertThat(service.status().lastPingOk()).isFalse();
    }

    @Test
    void missingSettingsRowIsAnError() {
        when(repository.findById(SystemSettings.ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service("").setEnabled(true)).isInstanceOf(IllegalStateException.class);
    }
}
