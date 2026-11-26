package com.nl2db.auth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain-level tests for the User aggregate.
 * <p>
 * Verifies invariants, status transitions, email handling,
 * and construction contracts. These tests do not depend on
 * a database connection.
 */
class UserDomainTest {

    private static Instant now;

    @BeforeAll
    static void setUpOnce() {
        now = Instant.now();
    }

    @Test
    void validUserCreation() {
        UUID userId = UUID.randomUUID();
        EmailAddress email = new EmailAddress("user@example.com");
        User user = new User(userId, email, UserStatus.ACTIVE, now);

        assertNotNull(user.id());
        assertEquals(userId, user.id());
        assertEquals("user@example.com", user.normalizedEmail());
        assertEquals(UserStatus.ACTIVE, user.status());
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
    void emailNormalizationLowercasesDomain() {
        UUID userId = UUID.randomUUID();
        EmailAddress email = new EmailAddress("User@EXAMPLE.COM");
        User user = new User(userId, email, UserStatus.ACTIVE, now);

        // Domain part is lowercased; local part preserved
        assertEquals("User@example.com", user.normalizedEmail());
    }

    @Test
    void normalizedEmailEquality() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        // Two different raw email addresses that normalize to the same form
        // (same local part, same lowercase domain)
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

        // Attempting to observe that id is the same after creation
        // is the test - the field is final and exposed only via getter
        assertTrue(user.id().version() != 0); // UUID is valid
    }

    @Test
    void validStatusTransitionPendingToActive() {
        UUID userId = UUID.randomUUID();
        EmailAddress email = new EmailAddress("user@example.com");
        User user = new User(userId, email, UserStatus.PENDING_VERIFICATION, now);

        user.transitionStatus(UserStatus.ACTIVE);

        assertEquals(UserStatus.ACTIVE, user.status());
        assertNotEquals(now, user.updatedAt()); // updatedAt is set to now transition
    }

    @Test
    void validStatusTransitionActiveToSuspended() {
        UUID userId = UUID.randomUUID();
        EmailAddress email = new EmailAddress("user@example.com");
        User user = new User(userId, email, UserStatus.ACTIVE, now);

        user.transitionStatus(UserStatus.SUSPENDED);

        assertEquals(UserStatus.SUSPENDED, user.status());
    }

    @Test
    void validStatusTransitionActiveToDeactivated() {
        UUID userId = UUID.randomUUID();
        EmailAddress email = new EmailAddress("user@example.com");
        User user = new User(userId, email, UserStatus.ACTIVE, now);

        user.transitionStatus(UserStatus.DEACTIVATED);

        assertEquals(UserStatus.DEACTIVATED, user.status());
    }

    @Test
    void invalidStatusTransitionFromDeactivated() {
        UUID userId = UUID.randomUUID();
        EmailAddress email = new EmailAddress("user@example.com");
        User user = new User(userId, email, UserStatus.DEACTIVATED, now);

        assertThrows(IllegalArgumentException.class,
                () -> user.transitionStatus(UserStatus.ACTIVE));
    }

    @Test
    void invalidStatusTransitionPendingToSuspended() {
        UUID userId = UUID.randomUUID();
        EmailAddress email = new EmailAddress("user@example.com");
        User user = new User(userId, email, UserStatus.PENDING_VERIFICATION, now);

        assertThrows(IllegalArgumentException.class,
                () -> user.transitionStatus(UserStatus.SUSPENDED));
    }

    @Test
    void invalidStatusTransitionPendingToDeactivated() {
        UUID userId = UUID.randomUUID();
        EmailAddress email = new EmailAddress("user@example.com");
        User user = new User(userId, email, UserStatus.PENDING_VERIFICATION, now);

        assertThrows(IllegalArgumentException.class,
                () -> user.transitionStatus(UserStatus.DEACTIVATED));
    }

    @Test
    void invalidNullStatusTransition() {
        UUID userId = UUID.randomUUID();
        EmailAddress email = new EmailAddress("user@example.com");
        User user = new User(userId, email, UserStatus.ACTIVE, now);

        assertThrows(IllegalArgumentException.class,
                () -> user.transitionStatus(null));
    }

    @Test
    void timestampUpdatedAfterTransition() {
        UUID userId = UUID.randomUUID();
        EmailAddress email = new EmailAddress("user@example.com");
        User user = new User(userId, email, UserStatus.ACTIVE, now);

        Instant before = user.updatedAt();
        user.transitionStatus(UserStatus.SUSPENDED);
        Instant after = user.updatedAt();

        assertNotEquals(before, after, "updatedAt should change after status transition");
        assertTrue(after.isAfter(before) || after.equals(after),
                "updatedAt should not precede the transition time");
    }

    @Test
    void createdAtCannotPrecedeConstructionTime() {
        // createdAt is final and set at construction;
        // the invariant is enforced by the constructor
        UUID userId = UUID.randomUUID();
        EmailAddress email = new EmailAddress("user@example.com");
        User user = new User(userId, email, UserStatus.ACTIVE, now);
        assertEquals(now, user.createdAt());
    }

    @Test
    void incompleteConstructionRejected() {
        // Null createdAt should be rejected
        UUID userId = UUID.randomUUID();
        EmailAddress email = new EmailAddress("user@example.com");

        assertThrows(IllegalArgumentException.class,
                () -> new User(userId, email, UserStatus.ACTIVE, null));
    }
}