package com.nl2db.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Organization membership association, linking a user to an organization
 * with a specific role and status.
 * <p>
 * Invariants:
 * - A user can have at most one membership per organization (enforced by
 *   unique constraint on user_id + organization_id)
 * - The role assigned at membership creation determines initial permissions
 * - Membership status (ACTIVE, SUSPENDED, REMOVED) governs authorization
 * - Owner membership is specially protected - OWNER cannot be demoted or
 *   removed through ordinary membership operations
 * - Foreign key constraints enforce referential integrity with users and
 *   organizations
 * <p>
 * This entity is owned by nl2db-auth. Permissions are evaluated via the
 * nl2db.security.RolePermissionMapping and nl2db.security.PermissionEvaluator.
 */
@Entity
@Table(name = "organization_memberships", schema = "nl2db",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"user_id", "organization_id"},
                name = "uk_organization_memberships_user_org"))
public class OrganizationMembership {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private final UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private final UUID userId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private final UUID organizationId;

    @Column(name = "role", nullable = false)
    private final String role;

    @Column(name = "membership_status", nullable = false)
    private final String membershipStatus;

    @Column(name = "joined_at", nullable = false, columnDefinition = "timestamp with time zone")
    private final Instant joinedAt;

    @Column(name = "updated_at", columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    // JPA-only default constructor
    @Deprecated // JPA only
    public OrganizationMembership() {
        this.id = null;
        this.userId = null;
        this.organizationId = null;
        this.role = null;
        this.membershipStatus = null;
        this.joinedAt = null;
        this.updatedAt = null;
    }

    /**
     * Constructs a new OrganizationMembership with the given identity, role,
     * and status.
     * <p>
     * This is the only valid way to create a Membership instance. All invariants
     * are enforced.
     *
     * @param id          stable UUID-based identifier (immutable after creation)
     * @param userId      the associated user UUID (immutable after creation)
     * @param organizationId the associated organization UUID (immutable after creation)
     * @param role        the user's role in the organization (OWNER, ADMIN, MEMBER)
     * @param membershipStatus the initial membership status
     * @param joinedAt    membership joinit timestamp (must not be null)
     * @throws IllegalArgumentException if any invariant is violated
     */
    public OrganizationMembership(UUID id, UUID userId, UUID organizationId,
                                   String role, String membershipStatus,
                                   Instant joinedAt) {
        this.id = id;
        this.userId = userId;
        this.organizationId = organizationId;
        this.role = role;
        this.membershipStatus = membershipStatus;
        this.joinedAt = joinedAt;
        this.updatedAt = joinedAt;

        // Invariant: id must not be null
        if (id == null) {
            throw new IllegalArgumentException("Membership ID must not be null");
        }

        // Invariant: userId must not be null
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }

        // Invariant: organizationId must not be null
        if (organizationId == null) {
            throw new IllegalArgumentException("Organization ID must not be null");
        }

        // Invariant: role must not be null
        if (role == null || role.trim().isEmpty()) {
            throw new IllegalArgumentException("Role must not be null or empty");
        }

        // Invariant: membershipStatus must not be null
        if (membershipStatus == null || membershipStatus.trim().isEmpty()) {
            throw new IllegalArgumentException("Membership status must not be null or empty");
        }

        // Invariant: joinedAt must not be null
        if (joinedAt == null) {
            throw new IllegalArgumentException("Joined-at timestamp must not be null");
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
     * Returns the user ID.
     * This is the UUID of the authenticated user who is a member of the
     * organization.
     */
    public UUID userId() {
        return userId;
    }

    /**
     * Returns the organization ID.
     * This is the UUID of the organization the user is membership of.
     */
    public UUID organizationId() {
        return organizationId;
    }

    /**
     * Returns the user's role in the organization.
     * Possible values: OWNER, ADMIN, MEMBER
     */
    public String role() {
        return role;
    }

    /**
     * Returns the current membership status.
     * Possible values: ACTIVE, SUSPENDED, REMOVED
     */
    public String membershipStatus() {
        return membershipStatus;
    }

    /**
     * Returns the timestamp when the user joined the organization.
     */
    public Instant joinedAt() {
        return joinedAt;
    }

    /**
     * Returns the last-updated timestamp.
     * This always precedes or equals the current time.
     */
    public Instant updatedAt() {
        return updatedAt;
    }

    /**
     * Updates the updatedAt timestamp.
     * Called internally after any state change.
     */
    public void touch() {
        this.updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrganizationMembership that = (OrganizationMembership) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}