package com.smartteach.common.mail;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Production: Brevo's transactional email API over HTTPS (port 443, not blocked on Render). */
class BrevoEmailSender implements EmailSender {

    private static final String URL = "https://api.brevo.com/v3/smtp/email";

    private final RestClient client;
    private final String from;

    BrevoEmailSender(String apiKey, String from) {
        // Runs on the scheduler thread (OutboxSender): a hanging provider must not block it for long.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder().requestFactory(requestFactory).defaultHeader("api-key", apiKey).build();
        this.from = from;
    }

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
