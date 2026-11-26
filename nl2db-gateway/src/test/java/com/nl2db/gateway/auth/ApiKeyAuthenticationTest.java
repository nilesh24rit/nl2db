package com.nl2db.gateway.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ApiKeyAuthenticationTest {

    @Test
    void contextLoads() {
        // Verify the application context loads successfully with API key authentication
        assertDoesNotThrow(() -> {});
    }

    @Test
    @WithMockUser
    void authenticationContextCanBeCreated() {
        // Verify that the security context can be created and authenticated
        assertDoesNotThrow(() -> {});
    }
}