package com.smartteach.common.mail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One mail waiting to be sent, or already sent / given up. See {@link MailQueue}. */
@Entity
@Table(name = "email_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "to_address", nullable = false, length = 254)
    private String toAddress;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(length = 10000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    EmailOutbox(String toAddress, String subject, String body, Instant now) {
        this.toAddress = toAddress;
        this.subject = subject;
        this.body = body;
        this.status = OutboxStatus.PENDING;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    void markSent(Instant now) {
        status = OutboxStatus.SENT;
        attempts++;
        sentAt = now;
        lastError = null;
        // A password reset mail holds a live link; nothing needs the text once it is delivered.
        body = null;
    }

    /** Next try after 1, 2, 4, 8 minutes; after {@code maxAttempts} tries the mail is given up. */
    void markFailed(String error, Instant now, int maxAttempts) {
        attempts++;
        lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
        if (attempts >= maxAttempts) {
            status = OutboxStatus.FAILED;
            body = null;
        } else {
            nextAttemptAt = now.plusSeconds(60L << (attempts - 1));
        }
    }
}
