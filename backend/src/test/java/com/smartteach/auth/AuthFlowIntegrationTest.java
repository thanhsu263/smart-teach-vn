package com.smartteach.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.smartteach.IntegrationTest;
import com.smartteach.auth.config.JwtConfig;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class AuthFlowIntegrationTest extends IntegrationTest {

    private static final String PASSWORD = "Secret123!";
    private static final String COOKIE = "refresh_token";

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void registerReturnsTokenAndCookieThenMeWorks() throws Exception {
        String email = uniqueEmail();
        MvcResult result = register(email)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.role").value("TEACHER"))
                .andReturn();

        Cookie cookie = result.getResponse().getCookie(COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/auth");

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken(result))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn A"));
    }

    @Test
    void emailIsCaseInsensitiveAndDuplicateIsConflict() throws Exception {
        String email = uniqueEmail();
        register(email).andExpect(status().isCreated());

        register(email.toUpperCase())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email is already registered"));
        login("  " + email.toUpperCase() + " ", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void databaseRejectsEmailsThatDifferOnlyInCase() throws Exception {
        String email = uniqueEmail();
        register(email).andExpect(status().isCreated());

        // Bypasses the service's lower-casing: the unique index on lower(email) is the last guard.
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO users (email, password_hash, full_name, role) VALUES (?, 'x', 'B', 'TEACHER')",
                        email.toUpperCase()))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void invalidRegisterBodyReturnsFieldErrors() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\",\"fullName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.fullName").exists());
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSame401() throws Exception {
        String email = uniqueEmail();
        register(email).andExpect(status().isCreated());

        login(email, "Wrong-password1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
        login(uniqueEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void protectedEndpointWithoutTokenIs401ProblemDetail() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Authentication required"));
    }

    @Test
    void expiredAccessTokenIs401() throws Exception {
        MvcResult registered = register(uniqueEmail()).andExpect(status().isCreated()).andReturn();
        String userId = JsonPath.read(registered.getResponse().getContentAsString(), "$.user.id").toString();

        // Expired well beyond the decoder's 60 s clock skew.
        Instant issued = Instant.now().minus(1, ChronoUnit.HOURS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .subject(userId)
                .issuedAt(issued)
                .expiresAt(issued.plus(15, ChronoUnit.MINUTES))
                .claim("role", "TEACHER")
                .build();
        String expired = jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedTokenIs401() throws Exception {
        String token = accessToken(register(uniqueEmail()).andReturn());
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(tampered)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesTokenAndIgnoresExpiredBearerHeader() throws Exception {
        MvcResult registered = register(uniqueEmail()).andReturn();
        Cookie first = registered.getResponse().getCookie(COOKIE);

        MvcResult refreshed = mvc.perform(post("/api/auth/refresh").cookie(first)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired.or.garbage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie second = refreshed.getResponse().getCookie(COOKIE);

        assertThat(second).isNotNull();
        assertThat(second.getValue()).isNotEqualTo(first.getValue());
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken(refreshed))))
                .andExpect(status().isOk());
    }

    @Test
    void reusingARotatedRefreshTokenRevokesTheWholeFamily() throws Exception {
        Cookie first = register(uniqueEmail()).andReturn().getResponse().getCookie(COOKIE);
        Cookie second = mvc.perform(post("/api/auth/refresh").cookie(first))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(COOKIE);

        // An attacker replays the old token...
        mvc.perform(post("/api/auth/refresh").cookie(first))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid refresh token"));
        // ...so the newest token in the chain stops working as well.
        mvc.perform(post("/api/auth/refresh").cookie(second))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutCookieIs401() throws Exception {
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE, "unknown")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutClearsCookieAndRevokesRefreshToken() throws Exception {
        Cookie cookie = register(uniqueEmail()).andReturn().getResponse().getCookie(COOKIE);

        MvcResult logout = mvc.perform(post("/api/auth/logout").cookie(cookie))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(logout.getResponse().getCookie(COOKIE).getMaxAge()).isZero();

        mvc.perform(post("/api/auth/refresh").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    private ResultActions register(String email) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s","fullName":"Nguyễn Văn A"}
                        """.formatted(email, PASSWORD)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
    }

    static String accessToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    static String bearer(String token) {
        return "Bearer " + token;
    }

    static String uniqueEmail() {
        return "teacher-" + UUID.randomUUID() + "@example.com";
    }
}
