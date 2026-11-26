package com.nl2db.auth;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for organization invitation creation.
 * <p>
 * Only the invited email and assigned role are required.
 * The inviter is derived from the authenticated JWT principal.
 */
public class InvitationCreateRequest {

    @NotNull(message = "Invited email must not be null")
    @Size(max = 255, message = "Invited email must not exceed 255 characters")
    private final String invitedEmail;

    @NotNull(message = "Role must not be null")
    private final OrganizationRole role;

    // Default constructor required by Spring deserialization
    public InvitationCreateRequest() {
        this.invitedEmail = null;
        this.role = null;
    }

    public InvitationCreateRequest(String invitedEmail, OrganizationRole role) {
        this.invitedEmail = invitedEmail;
        this.role = role;
    }

    public String invitedEmail() {
        return invitedEmail;
    }

    public OrganizationRole role() {
        return role;
    }

    @Override
    public String toString() {
        return "InvitationCreateRequest{invitedEmail='" + invitedEmail + "', role=" + role + "}";
    }
}