package com.smartteach.common.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartteach.IntegrationTest;
import com.smartteach.RecordingEmailSender;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class EmailOutboxIntegrationTest extends IntegrationTest {

    @Autowired
    MailQueue mailQueue;

    @Autowired
    RecordingEmailSender mails;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transaction;

    private final String to = "outbox-%s@example.com".formatted(UUID.randomUUID());

    @BeforeEach
    void sendWhatOtherTestsLeft() {
        // Otherwise a failure set up below could hit another test's mail.
        sendQueuedMails();
    }

    @Test
    void mailIsSentOnceAndItsTextIsNotKept() {
        mailQueue.enqueue(to, "Xin chào", "Nội dung");
        sendQueuedMails();
        sendQueuedMails();

        assertThat(mails.sentTo(to)).singleElement()
                .satisfies(mail -> assertThat(mail.text()).isEqualTo("Nội dung"));
        assertThat(row()).containsEntry("status", "SENT").containsEntry("attempts", 1).containsEntry("body", null);
        assertThat(row().get("sent_at")).isNotNull();
    }

    @Test
    void failedSendIsRetriedAfterABackoff() {
        mails.failNext(1);
        mailQueue.enqueue(to, "Xin chào", "Nội dung");

        sendQueuedMails();
        assertThat(mails.sentTo(to)).isEmpty();
        assertThat(row()).containsEntry("status", "PENDING").containsEntry("attempts", 1)
                .containsEntry("last_error", "Mail provider unavailable");
        // Not retried straight away.
        sendQueuedMails();
        assertThat(mails.sentTo(to)).isEmpty();

        makeDueNow();
        sendQueuedMails();
        assertThat(mails.sentTo(to)).hasSize(1);
        assertThat(row()).containsEntry("status", "SENT").containsEntry("attempts", 2).containsEntry("last_error", null);
    }

    @Test
    void mailIsGivenUpAfterFiveFailedTries() {
        mails.failNext(OutboxSender.MAX_ATTEMPTS);
        mailQueue.enqueue(to, "Xin chào", "Nội dung");
        for (int i = 0; i < OutboxSender.MAX_ATTEMPTS; i++) {
            makeDueNow();
            sendQueuedMails();
        }
        assertThat(row()).containsEntry("status", "FAILED").containsEntry("attempts", OutboxSender.MAX_ATTEMPTS)
                .containsEntry("body", null);

        makeDueNow();
        sendQueuedMails();
        assertThat(mails.sentTo(to)).isEmpty();
    }

    @Test
    void rolledBackChangeQueuesNoMail() {
        transaction.executeWithoutResult(status -> {
            mailQueue.enqueue(to, "Xin chào", "Nội dung");
            status.setRollbackOnly();
        });
        sendQueuedMails();

        assertThat(mails.sentTo(to)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM email_outbox WHERE to_address = ?", Integer.class, to))
                .isZero();
    }

    private Map<String, Object> row() {
        return jdbc.queryForMap("SELECT * FROM email_outbox WHERE to_address = ?", to);
    }

    private void makeDueNow() {
        jdbc.update("UPDATE email_outbox SET next_attempt_at = now() - interval '1 second' WHERE to_address = ?", to);
    }
}
