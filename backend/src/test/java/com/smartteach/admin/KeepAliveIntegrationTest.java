package com.smartteach.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartteach.IntegrationTest;
import com.smartteach.admin.entity.SystemSettings;
import com.smartteach.admin.repository.SystemSettingsRepository;
import com.smartteach.admin.service.KeepAliveService;
import com.smartteach.user.entity.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class KeepAliveIntegrationTest extends IntegrationTest {

    private static final String URL = "/api/admin/keep-alive";

    @Autowired
    KeepAliveService keepAliveService;

    @Autowired
    SystemSettingsRepository settingsRepository;

    @AfterEach
    void switchOff() {
        // The Spring context (and this flag) is shared with the other integration tests.
        keepAliveService.setEnabled(false);
    }

    @Test
    void offByDefaultAndNoUrlInTests() throws Exception {
        perform(signInAs(Role.ADMIN), get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.targetUrl").doesNotExist())
                .andExpect(jsonPath("$.intervalMinutes").value(10));
    }

    @Test
    void adminTurnsItOnAndTheFlagIsSaved() throws Exception {
        perform(signInAs(Role.ADMIN), put(URL), "{\"enabled\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        assertThat(keepAliveService.isEnabled()).isTrue();
        assertThat(settingsRepository.findById(SystemSettings.ID)).get()
                .extracting(SystemSettings::isKeepAliveEnabled).isEqualTo(true);
    }

    @Test
    void missingFlagIs400() throws Exception {
        perform(signInAs(Role.ADMIN), put(URL), "{}").andExpect(status().isBadRequest());
    }

    @Test
    void teacherCannotChangeIt() throws Exception {
        perform(signInAs(Role.TEACHER), put(URL), "{\"enabled\":true}").andExpect(status().isForbidden());
        assertThat(keepAliveService.isEnabled()).isFalse();
    }
}
