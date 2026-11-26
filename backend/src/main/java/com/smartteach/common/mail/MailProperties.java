package com.smartteach.common.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code app.mail}. {@code brevoApiKey} set means "send through Brevo",
 * blank means "send through SMTP" ({@code spring.mail.*}). {@code linkBaseUrl} is the frontend
 * address that links in emails point to.
 */
@ConfigurationProperties("app.mail")
public record MailProperties(String from, String brevoApiKey, String linkBaseUrl) {
}
