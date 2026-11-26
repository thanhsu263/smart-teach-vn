package com.smartteach.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartteach.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class RequestIdIntegrationTest extends IntegrationTest {

    @Test
    void generatesAnIdAndPutsItInHeaderAndErrorBody() throws Exception {
        MvcResult result = mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String id = result.getResponse().getHeader(RequestIdFilter.HEADER);
        assertThat(UUID.fromString(id)).isNotNull();
        assertThat(result.getResponse().getContentAsString()).contains("\"requestId\":\"" + id + "\"");
    }

    @Test
    void keepsTheCallersId() throws Exception {
        mvc.perform(get("/api/me").header(RequestIdFilter.HEADER, "client-42"))
                .andExpect(header().string(RequestIdFilter.HEADER, "client-42"))
                .andExpect(jsonPath("$.requestId").value("client-42"));
    }

    @Test
    void replacesAnUnsafeId() throws Exception {
        MvcResult result = mvc.perform(get("/api/me").header(RequestIdFilter.HEADER, "fake\nlog line"))
                .andReturn();

        String id = result.getResponse().getHeader(RequestIdFilter.HEADER);
        assertThat(UUID.fromString(id)).isNotNull();
    }

    @Test
    void errorsBuiltBySpringAlsoCarryTheId() throws Exception {
        // Malformed JSON is answered by ResponseEntityExceptionHandler, not by our own handlers.
        mvc.perform(post("/api/auth/login").header(RequestIdFilter.HEADER, "bad-json")
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.requestId").value("bad-json"));
    }

    @Test
    void successfulResponsesHaveTheHeaderToo() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestIdFilter.HEADER));
    }
}
