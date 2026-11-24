package com.smartteach;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.smartteach.user.entity.Role;
import com.smartteach.user.entity.User;
import com.smartteach.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Base class for tests against the full app and a real PostgreSQL container. Every subclass
 * uses the same configuration, so Spring reuses one context and one container for all of them.
 */
// A real port so the live quiz tests can also connect over WebSocket.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** Registers a new teacher with a unique email and returns an {@code Authorization} header value. */
    protected String signUpTeacher() throws Exception {
        String body = """
                {"email":"teacher-%s@example.com","password":"Secret123!","fullName":"Giáo viên"}"""
                .formatted(UUID.randomUUID());
        String json = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(json, "$.accessToken");
    }

    /** Saves a user with the given role (registration only creates teachers), signs in, returns the header. */
    protected String signInAs(Role role) throws Exception {
        String email = "%s-%s@example.com".formatted(role.name().toLowerCase(), UUID.randomUUID());
        userRepository.save(new User(email, passwordEncoder.encode("Secret123!"), "Test " + role, role, Instant.now()));
        String body = """
                {"email":"%s","password":"Secret123!"}""".formatted(email);
        String json = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(json, "$.accessToken");
    }

    protected ResultActions perform(String auth, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, auth));
    }

    protected ResultActions perform(String auth, MockHttpServletRequestBuilder request, String json) throws Exception {
        return perform(auth, request.contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
