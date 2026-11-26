package com.nl2db.auth;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for membership role update.
 * <p>
 * Contains only the new role. The membership ID and organization ID
 * are derived from the URL path parameters.
 */
public class MembershipRoleUpdateRequest {

    @NotNull(message = "Role must not be null")
    private final OrganizationRole role;

    // Default constructor required by Spring deserialization
    public MembershipRoleUpdateRequest() {
        this.role = null;
    }

    public MembershipRoleUpdateRequest(OrganizationRole role) {
        this.role = role;
    }

    public OrganizationRole role() {
        return role;
    }

    @Override
    public String toString() {
        return "MembershipRoleUpdateRequest{role=" + role + "}";
    }
}