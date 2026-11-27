package com.smartteach.common.mail;

import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

/**
 * Sends due mails from the outbox. Deliberately not one transaction: each row is saved right after
 * its own send, so a crash in the middle re-sends at most the mail that was in flight
 * (at-least-once delivery). The lock stops two instances from sending the same rows.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxSender {

    static final int MAX_ATTEMPTS = 5;
    static final int BATCH_SIZE = 20;

    private final EmailOutboxRepository repository;
    private final EmailSender emailSender;
    private final Clock clock;

    @SchedulerLock(name = "email-outbox", lockAtMostFor = "PT5M")
    public void sendDue() {
        List<EmailOutbox> due = repository.findByStatusAndNextAttemptAtLessThanEqualOrderById(
                OutboxStatus.PENDING, clock.instant(), Limit.of(BATCH_SIZE));
        for (EmailOutbox mail : due) {
            try {
                emailSender.send(mail.getToAddress(), mail.getSubject(), mail.getBody());
                mail.markSent(clock.instant());
            } catch (RuntimeException e) {
                mail.markFailed(e.getMessage(), clock.instant(), MAX_ATTEMPTS);
                log.warn("Mail {} failed (try {}): {}", mail.getId(), mail.getAttempts(), e.getMessage());
            }
            repository.save(mail);
        }
    }
}
