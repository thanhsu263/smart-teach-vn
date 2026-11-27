package com.smartteach.assignment.service;

import com.smartteach.assignment.repository.AttemptRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Student answers are personal data: they are kept 90 days after the attempt started, then deleted. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttemptRetentionJob {

    static final Duration RETENTION = Duration.ofDays(90);

    private final AttemptRepository attemptRepository;
    private final Clock clock;

    @Scheduled(cron = "${app.retention.cron:0 0 3 * * *}", zone = "UTC")
    @SchedulerLock(name = "attempt-retention")
    public void deleteOldAttempts() {
        Instant cutoff = clock.instant().minus(RETENTION);
        int deleted = attemptRepository.deleteStartedBefore(cutoff);
        log.info("Retention: deleted {} attempts started before {}", deleted, cutoff);
    }
}
