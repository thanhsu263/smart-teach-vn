package com.smartteach.common.mail;

/**
 * Sends a plain-text email. Locally this is SMTP to Mailpit; in production it is Brevo's HTTPS
 * API, because Render's free plan blocks outgoing SMTP ports. See {@link MailConfig}.
 */
public interface EmailSender {

    void send(String to, String subject, String text);
}
