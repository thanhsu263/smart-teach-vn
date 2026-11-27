package com.smartteach.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartteach.IntegrationTest;
import com.smartteach.RecordingEmailSender;
import com.smartteach.RecordingEmailSender.Mail;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

class PasswordResetIntegrationTest extends IntegrationTest {

    private static final String OLD_PASSWORD = "Secret123!";
    private static final String NEW_PASSWORD = "BrandNew456!";
    private static final Pattern LINK = Pattern.compile("http://localhost:5173/reset-password#token=([A-Za-z0-9_-]+)");

    @Autowired
    RecordingEmailSender mails;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void resetChangesThePasswordAndSignsOutEverywhere() throws Exception {
        String email = uniqueEmail();
        Cookie refreshCookie = register(email);

        forgot(email).andExpect(status().isNoContent());
        String token = tokenFromMail(email);

        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());

        login(email, OLD_PASSWORD).andExpect(status().isUnauthorized());
        login(email, NEW_PASSWORD).andExpect(status().isOk());
        // The session that existed before the reset is gone.
        mvc.perform(post("/api/auth/refresh").cookie(refreshCookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownEmailLooksTheSameButSendsNothing() throws Exception {
        String email = uniqueEmail();
        forgot(email).andExpect(status().isNoContent());
        assertThat(mails.sentTo(email)).isEmpty();
    }

    @Test
    void linkWorksOnlyOnce() throws Exception {
        String email = uniqueEmail();
        register(email);
        forgot(email);
        String token = tokenFromMail(email);

        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());
        reset(token, "Another789!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The reset link is invalid or has expired"));
        login(email, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void expiredLinkIsRefused() throws Exception {
        String email = uniqueEmail();
        register(email);
        forgot(email);
        String token = tokenFromMail(email);
        jdbc.update("""
                UPDATE password_reset_tokens SET expires_at = now() - interval '1 minute'
                WHERE user_id = (SELECT id FROM users WHERE email = ?)""", email);

        reset(token, NEW_PASSWORD).andExpect(status().isBadRequest());
        login(email, OLD_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void anOlderLinkStopsWorkingAfterAReset() throws Exception {
        String email = uniqueEmail();
        register(email);
        forgot(email);
        forgot(email);
        List<Mail> sent = mails.sentTo(email);
        assertThat(sent).hasSize(2);

        reset(token(sent.get(1)), NEW_PASSWORD).andExpect(status().isNoContent());
        reset(token(sent.get(0)), "Another789!").andExpect(status().isBadRequest());
    }

    @Test
    void tokenIsStoredOnlyAsAHash() throws Exception {
        String email = uniqueEmail();
        register(email);
        forgot(email);
        String token = tokenFromMail(email);

        List<String> stored = jdbc.queryForList(
                "SELECT token_hash FROM password_reset_tokens t JOIN users u ON u.id = t.user_id WHERE u.email = ?",
                String.class, email);
        assertThat(stored).hasSize(1).doesNotContain(token);
    }

    @Test
    void weakOrUnknownInputIsRefused() throws Exception {
        reset("not-a-real-token", NEW_PASSWORD).andExpect(status().isBadRequest());
        reset("anything", "short").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
        forgot("not-an-email").andExpect(status().isBadRequest());
    }

    private String tokenFromMail(String email) {
        List<Mail> sent = mails.sentTo(email);
        assertThat(sent).hasSize(1);
        return token(sent.getFirst());
    }

    private static String token(Mail mail) {
        Matcher matcher = LINK.matcher(mail.text());
        assertThat(matcher.find()).as("reset link in: %s", mail.text()).isTrue();
        return matcher.group(1);
    }

    private static String uniqueEmail() {
        return "reset-%s@example.com".formatted(UUID.randomUUID());
    }

    private Cookie register(String email) throws Exception {
        return json(post("/api/auth/register"), """
                {"email":"%s","password":"%s","fullName":"Cô Lan"}""".formatted(email, OLD_PASSWORD))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getCookie("refresh_token");
    }

    private ResultActions login(String email, String password) throws Exception {
        return json(post("/api/auth/login"), """
                {"email":"%s","password":"%s"}""".formatted(email, password));
    }

    /** Asks for a reset link and lets the outbox deliver it, as the background job would. */
    private ResultActions forgot(String email) throws Exception {
        ResultActions result = json(post("/api/auth/forgot-password"), """
                {"email":"%s"}""".formatted(email));
        sendQueuedMails();
        return result;
    }

    private ResultActions reset(String token, String password) throws Exception {
        return json(post("/api/auth/reset-password"), """
                {"token":"%s","newPassword":"%s"}""".formatted(token, password));
    }

    private ResultActions json(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String body)
            throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
