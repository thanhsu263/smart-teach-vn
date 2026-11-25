package com.smartteach.admin.service;

import com.smartteach.admin.config.KeepAliveProperties;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Free hosting plans (Render) put the backend to sleep after 15 minutes without traffic.
 * Calling our own public URL counts as traffic, so the server stays awake while this is enabled.
 * It cannot wake a server that is already asleep: the job only runs inside a running server.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeepAliveJob {

    // Liveness does not touch the database, so the free-plan database can still sleep.
    static final String PATH = "/actuator/health/liveness";

    private final KeepAliveService keepAliveService;
    private final KeepAliveProperties properties;
    private final RestClient keepAliveRestClient;

    @Scheduled(
            initialDelayString = "${app.keep-alive.interval-minutes:10}",
            fixedDelayString = "${app.keep-alive.interval-minutes:10}",
            timeUnit = TimeUnit.MINUTES)
    public void ping() {
        if (!keepAliveService.isEnabled() || !properties.configured()) {
            return;
        }
        boolean ok;
        try {
            keepAliveRestClient.get().uri(properties.url() + PATH).retrieve().toBodilessEntity();
            ok = true;
        } catch (RestClientException e) {
            log.warn("Keep-alive ping failed: {}", e.getMessage());
            ok = false;
        }
        keepAliveService.recordPing(ok);
    }
}
