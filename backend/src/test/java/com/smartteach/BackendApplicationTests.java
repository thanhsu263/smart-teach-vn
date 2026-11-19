package com.smartteach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class BackendApplicationTests extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayCreatesV1Schema() {
        Integer tables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'BASE TABLE' "
                        + "AND TABLE_NAME IN ('users','refresh_tokens','classes','students','quizzes','questions',"
                        + "'question_accepted_answers','options','assignments','attempts','attempt_answers')",
                Integer.class);
        assertThat(tables).isEqualTo(11);
    }

    @Test
    void healthEndpointIsPublicAndUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void openApiDocsArePublic() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("SmartTeach API"));
    }
}
