package com.smartteach.common.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/** Picks the mail transport: Brevo when BREVO_API_KEY is set, otherwise SMTP. */
@Slf4j
@Configuration
public class MailConfig {

    @Bean
    EmailSender emailSender(MailProperties properties, ObjectProvider<JavaMailSender> javaMailSender) {
        if (properties.brevoApiKey() != null && !properties.brevoApiKey().isBlank()) {
            log.info("Sending mail through the Brevo API");
            return new BrevoEmailSender(properties.brevoApiKey(), properties.from());
        }
        log.info("Sending mail through SMTP");
        return new SmtpEmailSender(javaMailSender.getObject(), properties.from());
    }
}
