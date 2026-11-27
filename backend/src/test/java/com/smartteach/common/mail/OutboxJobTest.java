package com.smartteach.common.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** The outbox is read only when there can be something to send (the free database sleeps otherwise). */
class OutboxJobTest {

    private final OutboxSender sender = mock(OutboxSender.class);
    private final EmailOutboxRepository repository = mock(EmailOutboxRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-27T00:00:00Z"), ZoneOffset.UTC);
    private final OutboxJob job = new OutboxJob(sender, repository, clock);

    @Test
    void readsOnceAfterStartupThenOnlyWhenWokenUp() {
        job.tick();
        job.tick();
        job.tick();
        verify(sender, times(1)).sendDue();

        job.wakeUp();
        job.tick();
        verify(sender, times(2)).sendDue();
    }

    @Test
    void keepsReadingWhileMailsArePending() {
        when(repository.existsByStatus(OutboxStatus.PENDING)).thenReturn(true, false);
        job.tick();
        job.tick();
        job.tick();
        verify(sender, times(2)).sendDue();
    }

    @Test
    void triesAgainNextTickWhenTheDatabaseIsDown() {
        doThrow(new IllegalStateException("database down")).doNothing().when(sender).sendDue();
        assertThatThrownBy(job::tick).isInstanceOf(IllegalStateException.class);
        job.tick();
        verify(sender, times(2)).sendDue();
    }

    @Test
    void cleanupKeepsPendingMailsAndThirtyDaysOfHistory() {
        job.deleteOld();
        verify(repository).deleteFinishedBefore(OutboxStatus.PENDING, clock.instant().minus(Duration.ofDays(30)));
        verify(sender, never()).sendDue();
    }

    @Test
    void backoffDoublesUntilTheMailIsGivenUp() {
        Instant now = clock.instant();
        EmailOutbox mail = new EmailOutbox("a@example.com", "s", "b", now);
        for (int minutes : new int[] {1, 2, 4, 8}) {
            mail.markFailed("boom", now, OutboxSender.MAX_ATTEMPTS);
            assertThat(mail.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(mail.getNextAttemptAt()).isEqualTo(now.plus(Duration.ofMinutes(minutes)));
        }
        mail.markFailed("x".repeat(600), now, OutboxSender.MAX_ATTEMPTS);
        assertThat(mail.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(mail.getLastError()).hasSize(500);
        assertThat(mail.getBody()).isNull();
    }
}
