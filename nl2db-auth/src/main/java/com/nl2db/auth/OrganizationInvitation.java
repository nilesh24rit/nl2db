package com.nl2db.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Organization invitation model, representing a pending invitation
 * for a user to join an organization.
 * <p>
 * Invariants:
 * - Each invitation has a unique SHA-256 token hash (never raw token)
 * - Tokens expire after a configured TTL (7 days initial)
 * - An invitation can be in one of several states (PENDING, ACCEPTED, REVOKED, EXPIRED)
 * - The email address on the invitation must match the invited user's email
 *   exactly during acceptance
 * - Invitations are scoped to a specific organization
 * - The inviter is recorded (may be null for system-generated invitations)
 * - Raw token value is never persisted; only the SHA-256 hash is stored
 * - Foreign key constraints enforce referential integrity with organizations
 * <p>
 * This entity is owned by nl2db-auth. Invitation authorization is
 * evaluated via the nl2db.security.PermissionEvaluator.
 */
@Entity
@Table(name = "organization_invitations", schema = "nl2db",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"token_hash"},
                name = "uk_organization_invitations_token_hash"))
public class OrganizationInvitation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private final UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private final UUID organizationId;

    @Column(name = "email", nullable = false, length = 255)
    private final String email;

    @Column(name = "token_hash", nullable = false, length = 64)
    private final String tokenHash;

    @Column(name = "inviter_user_id")
    private UUID inviterUserId;

    @Column(name = "status", nullable = false)
    private final String status;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamp with time zone")
    private final Instant createdAt;

    @Column(name = "expires_at", nullable = false, columnDefinition = "timestamp with time zone")
    private final Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /**
     * Constructs a new OrganizationInvitation with the given identity,
     * organization scope, and email target.
     * <p>
     * This is the only valid way to create an Invitation instance. All
     * invariants are enforced.
     *
     * @param id               stable UUID-based identifier (immutable after creation)
     * @param organizationId   the organization the invitation is for
     * @param email            the email address to invite
     * @param tokenHash        SHA-256 hash of the raw invitation token
     * @param status           initial status (PENDING, ACCEPTED, REVOKED, EXPIRED)
     * @param inviterUserId    the user ID of the inviter, may be null
     * @param createdAt        creation timestamp (must not be null)
     * @param expiresAt        expiration timestamp (must be after createdAt)
     * @throws IllegalArgumentException if any invariant is violated
     */
    public OrganizationInvitation(UUID id, UUID organizationId, String email,
                                   String tokenHash, String status,
                                   UUID inviterUserId, Instant createdAt,
                                   Instant expiresAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.email = email;
        this.tokenHash = tokenHash;
        this.status = status;
        this.inviterUserId = inviterUserId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.acceptedAt = null;
        this.revokedAt = null;

        // Invariant: id must not be null
        if (id == null) {
            throw new IllegalArgumentException("Invitation ID must not be null");
        }

        // Invariant: organizationId must not be null
        if (organizationId == null) {
            throw new IllegalArgumentException("Organization ID must not be null");
        }

        // Invariant: email must not be null or empty
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Invitation email must not be null or empty");
        }

        // Invariant: tokenHash must not be null or empty
        if (tokenHash == null || tokenHash.trim().isEmpty()) {
            throw new IllegalArgumentException("Token hash must not be null or empty");
        }

        // Invariant: status must not be null or empty
        if (status == null || status.trim().isEmpty()) {
            throw new IllegalArgumentException("Invitation status must not be null or empty");
        }

        // Invariant: createdAt must not be null
        if (createdAt == null) {
            throw new IllegalArgumentException("Creation timestamp must not be null");
        }

        // Invariant: expiresAt must not be null and must be after createdAt
        if (expiresAt == null) {
            throw new IllegalArgumentException("Expiration timestamp must not be null");
        }
        if (expiresAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "Expiration timestamp must be after creation timestamp");
        }
    }

    // --- Identity ---

    /**
     * Returns the stable UUID-based identifier.
     * Identity is immutable; this method never returns null.
     */
    public UUID id() {
        return id;
    }

    /**
     * Returns the organization ID.
     * This is the organization the invitation is scoped to.
     */
    public UUID organizationId() {
        return organizationId;
    }

    /**
     * Returns the email address to which the invitation was sent.
     */
    public String email() {
        return email;
    }

    /**
     * Returns the SHA-256 hash of the invitation token.
     * The raw token value is never persisted.
     */
    public String tokenHash() {
        return tokenHash;
    }

    /**
     * Returns the user ID of the inviter, or null if the inviter is
     * unknown or the invitation was system-generated.
     */
    public UUID inviterUserId() {
        return inviterUserId;
    }

    /**
     * Returns the current invitation status.
     * Possible values: PENDING, ACCEPTED, REVOKED, EXPIRED
     */
    public String status() {
        return status;
    }

    /**
     * Returns the creation timestamp.
     */
    public Instant createdAt() {
        return createdAt;
    }

    /**
     * Returns the expiration timestamp.
     * Invitations that have passed this timestamp are considered expired.
     */
    public Instant expiresAt() {
        return expiresAt;
    }

    /**
     * Returns the timestamp when the invitation was accepted, or null
     * if it has not been accepted yet.
     */
    public Instant acceptedAt() {
        return acceptedAt;
    }

    /**
     * Returns the timestamp when the invitation was revoked, or null
     * if it has not been revoked yet.
     */
    public Instant revokedAt() {
        return revokedAt;
    }

    /**
     * Checks if this invitation is still pending (not accepted, revoked,
     * or expired).
     */
    public boolean isPending() {
        return "PENDING".equals(status);
    }

    /**
     * Checks if this invitation has been accepted.
     */
    public boolean isAccepted() {
        return "ACCEPTED".equals(status);
    }

    /**
     * Checks if this invitation has been revoked.
     */
    public boolean isRevoked() {
        return "REVOKED".equals(status);
    }

    /**
     * Checks if this invitation has expired.
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Checks if this invitation can still be accepted (PENDING and not expired).
     */
    public boolean canBeAccepted() {
        return isPending() && !isExpired();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrganizationInvitation that = (OrganizationInvitation) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}