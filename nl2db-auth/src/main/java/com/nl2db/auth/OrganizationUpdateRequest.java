package com.nl2db.auth;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for organization name update.
 * <p>
 * Contains only the new organization name. Owner ID and user ID are
 * not accepted from the request; the authenticated user's identity
 * from the JWT principal is always used.
 */
public class OrganizationUpdateRequest {

    @NotNull(message = "Organization name must not be null")
    @Size(min = 1, max = 255, message = "Organization name must be between 1 and 255 characters")
    private final String name;

    public OrganizationUpdateRequest() {
        // Default constructor required by Spring deserialization
        this.name = null;
    }

    public OrganizationUpdateRequest(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return "OrganizationUpdateRequest{name='" + name + "'}";
    }
}