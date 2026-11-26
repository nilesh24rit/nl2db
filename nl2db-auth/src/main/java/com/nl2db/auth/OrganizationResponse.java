package com.nl2db.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for organization representation.
 * <p>
 * Returns organization details without exposing internal owner credential
 * information or sensitive user data. Fields are kept consistent with
 * the domain invariants and retrieval requirements.
 */
public class OrganizationResponse {

    private final UUID id;
    private final String name;
    private final String slug;
    private final OrganizationStatus status;
    private final Instant createdAt;

    public OrganizationResponse() {
        // Default constructor required by Spring
        this.id = null;
        this.name = null;
        this.slug = null;
        this.status = null;
        this.createdAt = null;
    }

    public OrganizationResponse(UUID id, String name, String slug, OrganizationStatus status, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String slug() {
        return slug;
    }

    public OrganizationStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "OrganizationResponse{id=" + id + ", name='" + name + "', slug='" + slug + "', status=" + status + ", createdAt=" + createdAt + "}";
    }
}