package com.nl2db.auth;

/**
 * Account status for an organization.
 * Permitted state transitions are enforced by the Organization aggregate.
 */
public enum OrganizationStatus {

    ACTIVE("active"),
    SUSPENDED("suspended"),
    DELETED("deleted");

    private final String value;

    OrganizationStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static OrganizationStatus fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (OrganizationStatus status : values()) {
            if (status.value.equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown organization status: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
