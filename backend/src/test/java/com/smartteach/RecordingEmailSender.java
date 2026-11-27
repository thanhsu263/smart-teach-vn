package com.smartteach;

import com.smartteach.common.mail.EmailSender;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Keeps sent mails in memory instead of talking to SMTP or Brevo; can pretend the provider is down. */
public class RecordingEmailSender implements EmailSender {

    public record Mail(String to, String subject, String text) {
    }

    private final List<Mail> sent = new CopyOnWriteArrayList<>();
    private final AtomicInteger failuresLeft = new AtomicInteger();

    @Override
    public void send(String to, String subject, String text) {
        if (failuresLeft.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            throw new IllegalStateException("Mail provider unavailable");
        }
        sent.add(new Mail(to, subject, text));
    }

    /** The next {@code count} sends throw, like a provider that is down. */
    public void failNext(int count) {
        failuresLeft.set(count);
    }

    public List<Mail> sentTo(String to) {
        return sent.stream().filter(mail -> mail.to().equals(to)).toList();
    }
}
