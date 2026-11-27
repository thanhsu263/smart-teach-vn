package com.smartteach.common.mail;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Outbox pattern: a mail is saved as a row in the caller's transaction, so it exists exactly when
 * the change that caused it is committed (a rolled-back close sends no summary). {@link OutboxJob}
 * sends it afterwards, with retries, so a slow or failing mail provider never breaks a request.
 */
@Service
@RequiredArgsConstructor
public class MailQueue {

    private final EmailOutboxRepository repository;
    private final OutboxJob outboxJob;
    private final Clock clock;

    public void enqueue(String to, String subject, String text) {
        repository.save(new EmailOutbox(to, subject, text, clock.instant()));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // Wake the sender only after commit; before that it cannot see the row.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    outboxJob.wakeUp();
                }
            });
        } else {
            outboxJob.wakeUp();
        }
    }
}
