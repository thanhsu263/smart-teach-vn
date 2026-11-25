package com.smartteach.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartteach.IntegrationTest;
import com.smartteach.user.entity.Role;
import org.junit.jupiter.api.Test;

/** Role checks against a real ADMIN-only endpoint. */
class RoleAccessIntegrationTest extends IntegrationTest {

    private static final String ADMIN_ONLY = "/api/admin/keep-alive";

    @Test
    void anonymousGets401() throws Exception {
        mvc.perform(get(ADMIN_ONLY)).andExpect(status().isUnauthorized());
    }

    @Test
    void teacherGets403() throws Exception {
        perform(signInAs(Role.TEACHER), get(ADMIN_ONLY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Access denied"));
    }

    @Test
    void adminGets200() throws Exception {
        perform(signInAs(Role.ADMIN), get(ADMIN_ONLY)).andExpect(status().isOk());
    }
}
