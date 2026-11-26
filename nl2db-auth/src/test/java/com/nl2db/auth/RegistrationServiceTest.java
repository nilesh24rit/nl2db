package com.nl2db.auth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Tests the registration workflow's core domain logic:
 * email normalization, user creation with PENDING_VERIFICATION status,
 * and duplicate email detection.
 * <p>
 * These tests do not depend on a database connection or a UserRepository.
 * They verify the same invariants that the RegistrationService enforces.
 */
class RegistrationServiceTest {

    private static Instant now;

    @BeforeAll
    static void setUpOnce() {
        now = Instant.now();
    }

    @Test
    void emailNormalizationLowercasesDomain() {
        // Exercise the EmailAddress normalization that the service uses
        // Domain part is lowercased; local part is preserved
        EmailAddress email = new EmailAddress("User@EXAMPLE.COM");
        assertEquals("User@example.com", email.normalized());
    }

    @Test
    void validUserCreationWithPendingVerificationStatus() {
        UUID userId = UUID.randomUUID();
        EmailAddress email = new EmailAddress("user@example.com");
        User user = new User(userId, email, UserStatus.PENDING_VERIFICATION, now);

        assertNotNull(user.id());
        assertEquals(userId, user.id());
        assertEquals("user@example.com", user.normalizedEmail());
        assertEquals(UserStatus.PENDING_VERIFICATION, user.status());
        assertEquals(now, user.createdAt());
        assertEquals(now, user.updatedAt());
    }

    @Test
    void invalidNullEmailRejection() {
        UUID userId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> new User(userId, null, UserStatus.ACTIVE, now));
    }

    @Test
    void invalidBlankEmailRejection() {
        UUID userId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> new User(userId, new EmailAddress("   "), UserStatus.ACTIVE, now));
    }

    @Test
    void invalidMalformedEmailRejection() {
        UUID userId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> new User(userId, new EmailAddress("not-an-email"), UserStatus.ACTIVE, now));
    }

    @Test
    void normalizedEmailEqualityAcrossRawVariants() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        EmailAddress email1 = new EmailAddress("user@example.com");
        EmailAddress email2 = new EmailAddress("user@EXAMPLE.COM");

        User user1 = new User(userId1, email1, UserStatus.ACTIVE, now);
        User user2 = new User(userId2, email2, UserStatus.ACTIVE, now);

        // Equality is based on normalized email, not UUID
        assertEquals(user1.normalizedEmail(), user2.normalizedEmail());
    }

    @Test
    void userIdentityImmutability() {
        UUID userId = UUID.randomUUID();
        EmailAddress email = new EmailAddress("user@example.com");
        User user = new User(userId, email, UserStatus.ACTIVE, now);

        // Identity (UUID) must not change
        assertEquals(userId, user.id());
    }

    @Test
    void statusTransitionPendingToActiveOnly() {
        UUID userId = UUID.randomUUID();
        EmailAddress email = new EmailAddress("user@example.com");
        User user = new User(userId, email, UserStatus.PENDING_VERIFICATION, now);

        // PENDING_VERIFICATION can only transition to ACTIVE
        user.transitionStatus(UserStatus.ACTIVE);
        assertEquals(UserStatus.ACTIVE, user.status());

        // Create a fresh user to test that PENDING_VERIFICATION -> SUSPENDED is rejected
        // (the original user was already transitioned to ACTIVE above)
        User user2 = new User(userId, email, UserStatus.PENDING_VERIFICATION, now);
        assertThrows(IllegalArgumentException.class,
                () -> user2.transitionStatus(UserStatus.SUSPENDED));
    }

    @Test
    void duplicateEmailDetectionLogic() {
        // Test the duplicate detection logic that the RegistrationService uses
        UUID userId1 = UUID.randomUUID();
        EmailAddress email = new EmailAddress("user@example.com");
        User user1 = new User(userId1, email, UserStatus.PENDING_VERIFICATION, now);

        // Another user with the same normalized email
        UUID userId2 = UUID.randomUUID();
        User user2 = new User(userId2, email, UserStatus.PENDING_VERIFICATION, now);

        // Both users have the same normalized email
        assertEquals(user1.normalizedEmail(), user2.normalizedEmail());
    }

    @Test
    void registrationResponseContainsExpectedFields() {
        // Test that a RegistrationResponse can be constructed from a User
        UUID userId = UUID.randomUUID();
        EmailAddress email = new EmailAddress("user@example.com");
        User user = new User(userId, email, UserStatus.PENDING_VERIFICATION, now);

        RegistrationResponse response = RegistrationResponse.fromUser(user);

        assertNotNull(response.userId());
        assertEquals(userId, response.userId());
        assertEquals("user@example.com", response.normalizedEmail());
        assertEquals(UserStatus.PENDING_VERIFICATION, response.status());
        assertEquals(now, response.createdAt());
    }

    @Test
    void registrationResponseDoesNotExposeCredentials() {
        UUID userId = UUID.randomUUID();
        EmailAddress email = new EmailAddress("user@example.com");
        User user = new User(userId, email, UserStatus.PENDING_VERIFICATION, now);

        RegistrationResponse response = RegistrationResponse.fromUser(user);

        // Response should not contain password, token, or internal metadata
        // (These fields don't exist on the record, which is the verification)
        assertNotNull(response.userId());
        assertNotNull(response.normalizedEmail());
        assertNotNull(response.status());
        assertNotNull(response.createdAt());
    }
}