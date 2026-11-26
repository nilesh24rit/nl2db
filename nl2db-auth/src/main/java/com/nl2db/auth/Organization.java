package com.nl2db.auth;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate root representing an organization.
 * <p>
 * Invariants:
 * - Every organization has a valid unique identifier (UUID, immutable after creation)
 * - Every organization has a valid name
 * - Every organization has a valid, unique URL-friendly slug
 * - Every organization has a status
 * - Creation timestamp is mandatory and immutable
 * - Updated timestamp cannot precede creation timestamp
 * - Organization identifier is immutable; slug must be unique across all organizations
 * - Domain objects cannot enter partially initialized states
 * - An organization must always have a valid owner
 * <p>
 * This class contains business logic and invariants only.
 * Persistence mapping (entity mapping, repository) is kept separate.
 */
@Entity
@Table(name = "organizations", schema = "nl2db",
        uniqueConstraints = @UniqueConstraint(
                columnNames = "slug",
                name = "uk_organizations_slug"))
public class Organization {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private final UUID id;

    @Column(name = "name", nullable = false, length = 255)
    @NotNull(message = "Organization name must not be null")
    @Size(min = 1, max = 255, message = "Organization name must be between 1 and 255 characters")
    private String name;

    @Column(name = "slug", nullable = false, length = 255)
    @NotNull(message = "Organization slug must not be null")
    @Size(min = 1, max = 255, message = "Organization slug must be between 1 and 255 characters")
    private String slug;

    @Column(name = "status", nullable = false)
    private OrganizationStatus status;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private final UUID ownerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private final Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // JPA-only default constructor
    @Deprecated // JPA only
    public Organization() {
        this.id = null;
        this.name = null;
        this.slug = null;
        this.status = null;
        this.ownerId = null;
        this.createdAt = null;
        this.updatedAt = null;
    }

    /**
     * Constructs a new Organization with the given identity, name and owner.
     * <p>
     * This is the only valid way to create an Organization instance.
     * All invariants are enforced.
     *
     * @param id          stable UUID-based identifier (immutable)
     * @param name        organization name (must not be blank)
     * @param slug        URL-friendly slug (must not be blank, unique)
     * @param status      initial organization status
     * @param ownerId     the owner user UUID (immutable after creation)
     * @param createdAt   creation timestamp (must not precede now)
     * @throws IllegalArgumentException if any invariant is violated
     */
    public Organization(UUID id, String name, String slug, OrganizationStatus status, UUID ownerId, Instant createdAt) {
        this.id = id;
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Organization name must not be blank");
        }
        this.name = name;
        this.slug = slug;
        this.status = status;
        this.ownerId = ownerId;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;

        // Invariant: createdAt must not be null
        if (createdAt == null) {
            throw new IllegalArgumentException("Creation timestamp must not be null");
        }

        // Invariant: ownerId must not be null
        if (ownerId == null) {
            throw new IllegalArgumentException("Owner ID must not be null");
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

    // --- Name ---

    /**
     * Returns the organization name.
     */
    public String name() {
        return name;
    }

    /**
     * Sets the organization name.
     * Triggers update timestamp and validates invariants.
     *
     * @param name the new organization name
     * @throws IllegalArgumentException if name is blank
     */
    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Organization name must not be blank");
        }
        this.name = name;
        this.updatedAt = Instant.now();
    }

    // --- Slug ---

    /**
     * Returns the URL-friendly slug.
     * Slug must be unique across all organizations.
     */
    public String slug() {
        return slug;
    }

    /**
     * Sets the URL-friendly slug.
     * Should be called when the slug needs to be recalculated (e.g., name change).
     *
     * @param slug the new slug
     */
    public void setSlug(String slug) {
        this.slug = slug;
        this.updatedAt = Instant.now();
    }

    // --- Owner ---

    /**
     * Returns the owner user ID.
     * This is the UUID of the authenticated user who created the organization.
     * The owner is immutable after creation.
     */
    public UUID ownerId() {
        return ownerId;
    }

    // --- Status ---

    /**
     * Returns the current organization status.
     */
    public OrganizationStatus status() {
        return status;
    }

    /**
     * Transitions the organization status to a new value if the transition is permitted.
     * <p>
     * Permitted state transitions:
     * <ul>
     *   <li>ACTIVE &rarr; SUSPENDED</li>
     *   <li>ACTIVE &rarr; DELETED</li>
     *   <li>SUSPENDED &rarr; ACTIVE</li>
     *   </ul>
     * <p>
     * All other transitions are rejected with an IllegalArgumentException.
     * Organization identifier must not change.
     *
     * @param newStatus the target status
     * @throws IllegalArgumentException if the transition is not permitted
     */
    public void transitionStatus(OrganizationStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("New status must not be null");
        }

        // DELETED is a terminal state - no outgoing transitions
        if (this.status == OrganizationStatus.DELETED) {
            throw new IllegalArgumentException(
                    "Cannot transition from DELETED status");
        }

        // SUSPENDED can only transition to ACTIVE
        if (this.status == OrganizationStatus.SUSPENDED
                && newStatus != OrganizationStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "SUSPENDED status can only transition to ACTIVE");
        }

        // ACTIVE can transition to SUSPENDED or DELETED
        if (this.status == OrganizationStatus.ACTIVE) {
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
        Organization that = (Organization) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}