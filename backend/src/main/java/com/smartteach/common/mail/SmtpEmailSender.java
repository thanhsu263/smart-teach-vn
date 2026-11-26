package com.smartteach.common.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;

/** Local development: SMTP to Mailpit (web inbox on http://localhost:8025). */
@RequiredArgsConstructor
class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    // Async: the caller (forgot password) must answer at once, whether or not a mail is sent.
    @Async
    @Override
    public void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}
