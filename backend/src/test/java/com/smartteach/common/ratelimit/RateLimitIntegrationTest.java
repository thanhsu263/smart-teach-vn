package com.smartteach.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartteach.IntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Own Spring context: the rest of the suite runs with rate limiting switched off.
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.rules.student-start.capacity=2",
        "app.rate-limit.rules.live-join.capacity=2",
})
class RateLimitIntegrationTest extends IntegrationTest {

    @Autowired
    private MeterRegistry meterRegistry;

    private static MockHttpServletRequestBuilder from(String ip, MockHttpServletRequestBuilder request) {
        return request.with(r -> {
            r.setRemoteAddr(ip);
            return r;
        }).contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void sixthWrongLoginForSameEmailAndIpGets429() throws Exception {
        String body = """
                {"email":"victim@example.com","password":"wrong-password"}""";
        for (int i = 0; i < 5; i++) {
            mvc.perform(from("203.0.113.1", post("/api/auth/login")).content(body))
                    .andExpect(status().isUnauthorized());
        }

        MvcResult result = mvc.perform(from("203.0.113.1", post("/api/auth/login")).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail").exists())
                .andReturn();
        // 5 per minute: the next token comes at most 12 seconds later.
        assertThat(Integer.parseInt(result.getResponse().getHeader("Retry-After"))).isBetween(1, 12);

        // Another IP is not affected, so the real teacher can still sign in.
        mvc.perform(from("203.0.113.2", post("/api/auth/login")).content(body))
                .andExpect(status().isUnauthorized());
        assertThat(meterRegistry.counter("smartteach.ratelimit.rejected", "rule", "login").count())
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void studentStartIsLimitedPerIp() throws Exception {
        String body = """
                {"accessCode":"ABCDEFGHJK"}""";
        for (int i = 0; i < 2; i++) {
            mvc.perform(from("203.0.113.10", post("/api/public/assignments/0000000000/start")).content(body))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(from("203.0.113.10", post("/api/public/assignments/0000000000/start")).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void liveJoinIsLimitedPerIp() throws Exception {
        String body = """
                {"nickname":"Minh"}""";
        for (int i = 0; i < 2; i++) {
            mvc.perform(from("203.0.113.20", post("/api/public/live/000000/players")).content(body))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(from("203.0.113.20", post("/api/public/live/000000/players")).content(body))
                .andExpect(status().isTooManyRequests());
    }
}
