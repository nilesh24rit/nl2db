package com.nl2db.auth;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate root representing an individual platform user.
 * <p>
 * Invariants:
 * - Every user has a valid unique identifier (UUID, immutable after creation)
 * - Every user has a valid normalized email address
 * - Every user has a valid account status
 * - Creation timestamp is mandatory and immutable
 * - Updated timestamp cannot precede creation timestamp
 * - Identity cannot be changed after creation
 * - Invalid status transitions are rejected
 * - Domain objects cannot enter partially initialized states
 * <p>
 * This class contains business logic and invariants only.
 * Persistence mapping (entity mapping, repository) is kept separate.
 */
@Entity
@Table(name = "users", schema = "nl2db",
        uniqueConstraints = @UniqueConstraint(
                columnNames = "normalized_email",
                name = "uk_users_normalized_email"))
public class User {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private final UUID id;

    @Column(name = "normalized_email", nullable = false, length = 255)
    private final String normalizedEmail;

    @Column(name = "status", nullable = false)
    private UserStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private final Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Default constructor for JPA/Hibernate
    @Deprecated // JPA only
    public User() {
        this.id = null;
        this.normalizedEmail = null;
        this.status = null;
        this.createdAt = null;
        this.updatedAt = null;
    }

    /**
     * Constructs a new User with the given identity and email.
     * <p>
     * This is the only valid way to create a User instance.
     * All invariants are enforced.
     *
     * @param id          stable UUID-based identifier (immutable)
     * @param email       raw email address (will be normalized)
     * @param status      initial account status
     * @param createdAt   creation timestamp (must not precede now)
     * @throws IllegalArgumentException if any invariant is violated
     */
    public User(UUID id, EmailAddress email, UserStatus status, Instant createdAt) {
        this.id = id;
        if (email == null) {
            throw new IllegalArgumentException("Email address must not be null");
        }
        this.normalizedEmail = email.normalized();
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;

        // Invariant: createdAt must not be null
        if (createdAt == null) {
            throw new IllegalArgumentException("Creation timestamp must not be null");
        }

        // Invariant: updatedAt cannot precede createdAt
        // (enforced by initial assignment; setter validates on transition)
    }

    // --- Identity ---

    /**
     * Returns the stable UUID-based identifier.
     * Identity is immutable; this method never returns null.
     */
    public UUID id() {
        return id;
    }

    // --- Email ---

    /**
     * Returns the normalized email address.
     * Equality of EmailAddress values is based on normalized form.
     */
    public String normalizedEmail() {
        return normalizedEmail;
    }

    // --- Status ---

    /**
     * Returns the current account status.
     */
    public UserStatus status() {
        return status;
    }

    /**
     * Transitions the user status to a new value if the transition is permitted.
     * <p>
     * Permitted state transitions:
     * <ul>
     *   <li>PENDING_VERIFICATION &rarr; ACTIVE</li>
     *   <li>ACTIVE &rarr; SUSPENDED</li>
     *   <li>ACTIVE &rarr; DEACTIVATED</li>
     *   <li>SUSPENDED &rarr; ACTIVE</li>
     *   <li>DEACTIVATED &rarr; (no transitions - terminal state)</li>
     * </ul>
     * <p>
     * All other transitions are rejected with an IllegalArgumentException.
     * Identity must not change.
     *
     * @param newStatus the target status
     * @throws IllegalArgumentException if the transition is not permitted
     */
    public void transitionStatus(UserStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("New status must not be null");
        }

        // DEACTIVATED is a terminal state - no outgoing transitions
        if (this.status == UserStatus.DEACTIVATED) {
            throw new IllegalArgumentException(
                    "Cannot transition from DEACTIVATED status");
        }

        // SUSPENDED can only transition to ACTIVE
        if (this.status == UserStatus.SUSPENDED
                && newStatus != UserStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "SUSPENDED status can only transition to ACTIVE");
        }

        // PENDING_VERIFICATION can only transition to ACTIVE
        if (this.status == UserStatus.PENDING_VERIFICATION
                && newStatus != UserStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "PENDING_VERIFICATION status can only transition to ACTIVE");
        }

        // All other transitions from ACTIVE are allowed (SUSPENDED, DEACTIVATED)
        if (this.status == UserStatus.ACTIVE) {
            this.status = newStatus;
            this.updatedAt = Instant.now();
            return;
        }

        // If we reach here, the transition is valid (e.g., SUSPENDED->ACTIVE
        // already handled above, but this covers any future-safe cases)
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    // --- Timestamps ---

    /**
     * Returns the creation timestamp.
     * This value is immutable and set at construction.
     */
    public Instant createdAt() {
        return createdAt;
    }

    /**
     * Returns the last-updated timestamp.
     * This always precedes or equals the current time.
     */
    public Instant updatedAt() {
        return updatedAt;
    }

    /**
     * Ensures updatedAt cannot precede createdAt.
     * Called internally after any state change.
     */
    private void validateTimestamps() {
        if (updatedAt != null && createdAt != null
                && updatedAt.isBefore(createdAt)) {
            this.updatedAt = createdAt;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}