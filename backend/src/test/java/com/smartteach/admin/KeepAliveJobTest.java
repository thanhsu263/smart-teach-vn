package com.smartteach.admin;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.smartteach.admin.config.KeepAliveProperties;
import com.smartteach.admin.service.KeepAliveJob;
import com.smartteach.admin.service.KeepAliveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class KeepAliveJobTest {

    private static final String BASE = "https://api.example.com";

    @Mock
    KeepAliveService keepAliveService;

    private MockRestServiceServer server;
    private RestClient restClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
    }

    private KeepAliveJob job(String url) {
        return new KeepAliveJob(keepAliveService, new KeepAliveProperties(url, 10), restClient);
    }

    @Test
    void doesNothingWhenSwitchedOff() {
        when(keepAliveService.isEnabled()).thenReturn(false);

        job(BASE).ping();

        server.verify();
        verify(keepAliveService, never()).recordPing(anyBoolean());
    }

    @Test
    void doesNothingWithoutUrl() {
        when(keepAliveService.isEnabled()).thenReturn(true);

        job("").ping();

        server.verify();
        verify(keepAliveService, never()).recordPing(anyBoolean());
    }

    @Test
    void pingsLivenessAndRecordsSuccess() {
        when(keepAliveService.isEnabled()).thenReturn(true);
        server.expect(requestTo(BASE + "/actuator/health/liveness"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess());

        job(BASE).ping();

        server.verify();
        verify(keepAliveService).recordPing(true);
    }

    @Test
    void recordsFailureInsteadOfThrowing() {
        when(keepAliveService.isEnabled()).thenReturn(true);
        server.expect(requestTo(BASE + "/actuator/health/liveness")).andRespond(withServerError());

        job(BASE).ping();

        verify(keepAliveService).recordPing(false);
    }
}
