package com.nl2db.auth;

import java.util.UUID;

/**
 * Response DTO for membership representation.
 * <p>
 * Returns membership details without exposing internal user credential
 * information or sensitive user data. Fields are kept consistent with
 * the domain invariants and retrieval requirements.
 */
public class MembershipResponse {

    private final UUID userId;
    private final String role;
    private final String membershipStatus;

    public MembershipResponse() {
        // Default constructor required by Spring
        this.userId = null;
        this.role = null;
        this.membershipStatus = null;
    }

    public MembershipResponse(UUID userId, String role, String membershipStatus) {
        this.userId = userId;
        this.role = role;
        this.membershipStatus = membershipStatus;
    }

    public UUID userId() {
        return userId;
    }

    public String role() {
        return role;
    }

    public String membershipStatus() {
        return membershipStatus;
    }

    @Override
    public String toString() {
        return "MembershipResponse{userId=" + userId + ", role='" + role + "', membershipStatus='" + membershipStatus + "'}";
    }
}