package com.nl2db.auth;

/**
 * Account status for a user.
 * Permitted state transitions are enforced by the User aggregate.
 */
public enum UserStatus {

    ACTIVE("active"),
    SUSPENDED("suspended"),
    DEACTIVATED("deactivated"),
    PENDING_VERIFICATION("pending_verification"),
    DELETED("deleted");

    private final String value;

    UserStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static UserStatus fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (UserStatus status : values()) {
            if (status.value.equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown user status: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
