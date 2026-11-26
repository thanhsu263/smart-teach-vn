package com.smartteach.common.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartteach.IntegrationTest;
import com.smartteach.common.ratelimit.RateLimitRule;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MetricsIntegrationTest extends IntegrationTest {

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void prometheusEndpointIsNotPublic() throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/prometheus").with(httpBasic("prometheus", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void teacherTokenDoesNotOpenMetrics() throws Exception {
        String teacher = signUpTeacher();
        perform(teacher, get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    }

    @Test
    void scraperSeesTheCustomMetrics() throws Exception {
        // Counters only show up once they exist; the gauge is always there.
        meterRegistry.counter("smartteach.attempts.submitted");
        meterRegistry.counter("smartteach.ratelimit.rejected", "rule", RateLimitRule.LOGIN.name().toLowerCase());

        String body = mvc.perform(get("/actuator/prometheus").with(httpBasic("prometheus", "test-metrics-password")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .contains("smartteach_live_rooms_open")
                .contains("smartteach_attempts_submitted_total")
                .contains("smartteach_ratelimit_rejected_total{rule=\"login\"}");
    }
}
