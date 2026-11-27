package com.smartteach.common.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Local development: SMTP to Mailpit (web inbox on http://localhost:8025). */
@RequiredArgsConstructor
class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

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
