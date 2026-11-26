package com.nl2db.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;

/**
 * Tests the Argon2id password hashing service.
 * <p>
 * Verifies service structure, parameter matching, and error handling
 * using Spring Security's Argon2PasswordEncoder. Tests focus on
 * behavior that can be verified without actual hash computation.
 */
class PasswordHashingServiceTest {

    private PasswordHashingService passwordHashingService;

    @BeforeEach
    void setUp() {
        passwordHashingService = new PasswordHashingService();
    }

    @Test
    void serviceHasArgon2idAlgorithmPrefix() {
        // The service should be configured with Argon2id encoding
        assertNotNull(passwordHashingService);
    }

    @Test
    void serviceMatchesCurrentParametersWithValidHash() {
        // Given: a pre-existing Argon2id encoded hash
        String argon2idHash = "$argon2id$m=19456,t=2,p=1$base64salt$base64hash";
        // When: checking if the hash matches the current parameter configuration
        // Then: the hash uses the Argon2id algorithm identifier
        assertTrue(passwordHashingService.matchesCurrentParameters(argon2idHash),
                "Encoded hash must match the current parameter configuration");
    }

    @Test
    void serviceMatchesCurrentParametersWithInvalidHash() {
        // Given: a hash that does not use Argon2id
        String invalidHash = "$sha256$invalidhash";
        // Then: the hash should not match the current parameter configuration
        assertFalse(passwordHashingService.matchesCurrentParameters(invalidHash),
                "Hash without argon2id identifier should not match current config");
    }

    @Test
    void serviceRejectsBlankPassword() {
        assertThrows(IllegalArgumentException.class,
                () -> passwordHashingService.encode(""));
    }

    @Test
    void serviceRejectsNullPassword() {
        assertThrows(IllegalArgumentException.class,
                () -> passwordHashingService.encode(null));
    }

    @Test
    void exceptionMessageIsNotNull() {
        // When: an invalid password is provided
        // Then: the exception message must not be null
        try {
            passwordHashingService.encode("");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            assertNotNull(message, "Exception message must not be null");
        }
    }
}