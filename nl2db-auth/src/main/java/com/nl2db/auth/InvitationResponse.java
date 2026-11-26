package com.nl2db.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for invitation representation.
 * <p>
 * Returns invitation details without exposing internal token values or
 * sensitive user data. Fields are kept consistent with the domain
 * invariants and retrieval requirements.
 */
public class InvitationResponse {

    private final UUID id;
    private final String email;
    private final String status;
    private final Instant expiresAt;

    public InvitationResponse() {
        // Default constructor required by Spring
        this.id = null;
        this.email = null;
        this.status = null;
        this.expiresAt = null;
    }

    public InvitationResponse(UUID id, String email, String status, Instant expiresAt) {
        this.id = id;
        this.email = email;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String status() {
        return status;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    @Override
    public String toString() {
        return "InvitationResponse{id=" + id + ", email='" + email + "', status='" + status + "', expiresAt=" + expiresAt + "}";
    }
}