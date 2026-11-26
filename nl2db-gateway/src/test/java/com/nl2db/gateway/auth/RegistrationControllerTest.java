package com.nl2db.gateway.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * API-level tests for the registration endpoint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegistrationControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void successfulRegistrationReturnsCreated() throws Exception {
        ResultActions actions = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\"}"));

        actions.andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.normalizedEmail").value("user@example.com"))
                .andExpect(jsonPath("$.status").value("pending_verification"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void missingEmailReturnsBadRequest() throws Exception {
        ResultActions actions = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        actions.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void blankEmailReturnsBadRequest() throws Exception {
        ResultActions actions = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"   \"}"));

        actions.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void malformedEmailReturnsBadRequest() throws Exception {
        ResultActions actions = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\"}"));

        actions.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception {
        ResultActions actions = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\"}"));

        actions.andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("The specified email address is already registered. Each email address may only be used once."));
    }

    @Test
    void registrationResponseDoesNotExposeCredentials() throws Exception {
        ResultActions actions = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\"}"));

        actions.andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.salt").doesNotExist());
    }

    @Test
    void otherProtectedRoutesRemainProtected() throws Exception {
        ResultActions actions = mockMvc.perform(get("/api/v1/auth/me"));

        actions.andExpect(status().isUnauthorized() || status().isForbidden());
    }
}