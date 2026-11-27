package com.smartteach.common.mail;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives {@link OutboxSender}. The free-plan database (Neon) sleeps while nobody queries it, so
 * this job does not poll the table: it reads the outbox only after {@link #wakeUp()} (a mail was
 * queued), once after startup (rows left by the last run) and while rows are still pending (retries).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxJob {

    static final Duration KEEP_FINISHED = Duration.ofDays(30);

    private final OutboxSender sender;
    private final EmailOutboxRepository repository;
    private final Clock clock;
    private final AtomicBoolean pending = new AtomicBoolean(true);

    public void wakeUp() {
        pending.set(true);
    }

    @Scheduled(initialDelayString = "${app.mail.outbox-delay:10s}", fixedDelayString = "${app.mail.outbox-delay:10s}")
    public void tick() {
        // Cleared before reading, so a mail queued while we send sets it again and is not missed.
        if (!pending.getAndSet(false)) {
            return;
        }
        // Stays true if the database is down, so the next tick tries again.
        boolean more = true;
        try {
            sender.sendDue();
            // Retries waiting for their backoff, or more than one batch: look again next tick.
            more = repository.existsByStatus(OutboxStatus.PENDING);
        } finally {
            if (more) {
                pending.set(true);
            }
        }
    }

    /** Sent and failed rows are kept for a while only, to look into delivery problems. */
    @Scheduled(cron = "${app.mail.outbox-cleanup-cron:0 30 3 * * *}", zone = "UTC")
    @SchedulerLock(name = "email-outbox-cleanup", lockAtMostFor = "PT10M")
    public void deleteOld() {
        int deleted = repository.deleteFinishedBefore(OutboxStatus.PENDING, clock.instant().minus(KEEP_FINISHED));
        log.info("Outbox: deleted {} old mails", deleted);
    }
}
