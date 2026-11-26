package com.smartteach.common.mail;

import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.client.RestClient;

/** Production: Brevo's transactional email API over HTTPS (port 443, not blocked on Render). */
class BrevoEmailSender implements EmailSender {

    private static final String URL = "https://api.brevo.com/v3/smtp/email";

    private final RestClient client;
    private final String from;

    BrevoEmailSender(String apiKey, String from) {
        this.client = RestClient.builder().defaultHeader("api-key", apiKey).build();
        this.from = from;
    }

    // Async: the caller (forgot password) must answer at once, whether or not a mail is sent.
    @Async
    @Override
    public void send(String to, String subject, String text) {
        Map<String, Object> body = Map.of(
                "sender", Map.of("name", "SmartTeach", "email", from),
                "to", List.of(Map.of("email", to)),
                "subject", subject,
                "textContent", text);
        client.post().uri(URL).contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
