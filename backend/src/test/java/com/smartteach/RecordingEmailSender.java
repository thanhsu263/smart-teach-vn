package com.smartteach;

import com.smartteach.common.mail.EmailSender;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Keeps sent mails in memory (and synchronously) instead of talking to SMTP or Brevo. */
public class RecordingEmailSender implements EmailSender {

    public record Mail(String to, String subject, String text) {
    }

    private final List<Mail> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(String to, String subject, String text) {
        sent.add(new Mail(to, subject, text));
    }

    public List<Mail> sentTo(String to) {
        return sent.stream().filter(mail -> mail.to().equals(to)).toList();
    }
}
