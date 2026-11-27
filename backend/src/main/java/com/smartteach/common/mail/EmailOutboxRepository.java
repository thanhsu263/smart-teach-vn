package com.smartteach.common.mail;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

    List<EmailOutbox> findByStatusAndNextAttemptAtLessThanEqualOrderById(
            OutboxStatus status, Instant now, Limit limit);

    boolean existsByStatus(OutboxStatus status);

    /** Sent and failed mails created before {@code cutoff}; pending ones are never deleted. */
    @Modifying
    @Transactional
    @Query("delete from EmailOutbox m where m.status <> :pending and m.createdAt < :cutoff")
    int deleteFinishedBefore(OutboxStatus pending, Instant cutoff);
}
