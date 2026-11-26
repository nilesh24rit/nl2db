package com.nl2db.auth;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for organization creation.
 * <p>
 * Only the organization name is required; the authenticated user is
 * automatically assigned as owner. Request-supplied owner IDs are
 * explicitly rejected.
 */
public class OrganizationCreateRequest {

    @NotNull(message = "Organization name must not be null")
    @Size(min = 1, max = 255, message = "Organization name must be between 1 and 255 characters")
    private final String name;

    public OrganizationCreateRequest() {
        // Default constructor required by Spring deserialization
        this.name = null;
    }

    public OrganizationCreateRequest(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return "OrganizationCreateRequest{name='" + name + "'}";
    }
}