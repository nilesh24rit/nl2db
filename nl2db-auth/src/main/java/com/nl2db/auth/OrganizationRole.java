package com.nl2db.auth;

/**
 * Role within an organization membership.
 * These three roles are the only supported membership roles.
 * ADMIN cannot perform owner-only operations (owner removal,
 * demotion, organization deletion).
 * OWNER has full organization management authority.
 * MEMBER has limited access with no management capabilities.
 */
public enum OrganizationRole {
    OWNER("OWNER"),
    ADMIN("ADMIN"),
    MEMBER("MEMBER");

    private final String value;

    OrganizationRole(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /**
     * Returns true if this role is OWNER.
     */
    public boolean isOwner() {
        return this == OWNER;
    }

    /**
     * Returns true if this role is ADMIN or OWNER (i.e., can manage members).
     */
    public boolean isManagementRole() {
        return this == ADMIN || this == OWNER;
    }

    /**
     * Returns true if this role can perform owner-only operations
     * (ownership transfer, owner removal, organization deletion).
     */
    public boolean isOwnerOnly() {
        return this == OWNER;
    }
}