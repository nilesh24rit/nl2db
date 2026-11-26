package com.nl2db.auth;

import jakarta.validation.constraints.Size;

/**
 * Request DTO for invitation acceptance.
 * <p>
 * Contains the token hash of the invitation to accept.
 * The email matching and membership creation logic is handled
 * server-side; the client supplies only the token.
 * <p>
 * During acceptance, the server verifies:
 * - The token exists and is valid (SHA-256 hash match)
 * - The invitation is in PENDING status
 * - The invitation has not expired
 * - The authenticated user's email matches the invited email exactly
 * - No concurrent acceptance has already occurred
 */
public class InvitationAcceptRequest {

    @Size(max = 64, message = "Token hash must not exceed 64 characters")
    private final String tokenHash;

    public InvitationAcceptRequest() {
        // Default constructor required by Spring deserialization
        this.tokenHash = null;
    }

    public InvitationAcceptRequest(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String tokenHash() {
        return tokenHash;
    }

    @Override
    public String toString() {
        return "InvitationAcceptRequest{tokenHash='" + tokenHash + "'}";
    }
}