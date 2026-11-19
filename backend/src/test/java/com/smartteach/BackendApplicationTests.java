package com.smartteach;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class BackendApplicationTests {

    @LocalServerPort
    int port;

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
    void healthEndpointIsPublicAndUp() {
        String body = RestClient.create("http://localhost:" + port)
                .get().uri("/actuator/health")
                .retrieve().body(String.class);
        assertThat(body).contains("\"status\":\"UP\"");
    }

    @Test
    void otherEndpointsAreDenied() {
        int status = RestClient.create("http://localhost:" + port)
                .get().uri("/api/me")
                .exchange((req, res) -> res.getStatusCode().value());
        assertThat(status).isIn(401, 403);
    }
}
