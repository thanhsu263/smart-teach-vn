package com.smartteach.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartteach.IntegrationTest;
import com.smartteach.user.entity.Role;
import com.smartteach.user.entity.User;
import com.smartteach.user.repository.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Uses the test-only /api/test/admin-only endpoint guarded by hasRole('ADMIN'). */
class RoleAccessIntegrationTest extends IntegrationTest {

    private static final String ADMIN_ONLY = "/api/test/admin-only";

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void anonymousGets401() throws Exception {
        mvc.perform(get(ADMIN_ONLY)).andExpect(status().isUnauthorized());
    }

    @Test
    void teacherGets403() throws Exception {
        String token = loginAs(Role.TEACHER);
        mvc.perform(get(ADMIN_ONLY).header(HttpHeaders.AUTHORIZATION, AuthFlowIntegrationTest.bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Access denied"));
    }

    @Test
    void adminGets200() throws Exception {
        String token = loginAs(Role.ADMIN);
        mvc.perform(get(ADMIN_ONLY).header(HttpHeaders.AUTHORIZATION, AuthFlowIntegrationTest.bearer(token)))
                .andExpect(status().isOk());
    }

    private String loginAs(Role role) throws Exception {
        String email = AuthFlowIntegrationTest.uniqueEmail();
        userRepository.save(new User(email, passwordEncoder.encode("Secret123!"), "Test " + role, role, Instant.now()));
        return AuthFlowIntegrationTest.accessToken(mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Secret123!\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn());
    }
}
