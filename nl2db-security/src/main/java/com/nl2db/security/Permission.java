package com.nl2db.security;

/**
 * A strongly typed representation of a permissions string.
 * <p>
 * Permissions follow a resource:action naming convention (e.g.
 * "organization:read", "member:invite", "database:create").
 * This class validates the format and provides safe comparison
 * operations. Instances are effectively final and immutable.
 * <p>
 * Deny by default: if a permission string does not match the
 * expected pattern, it is rejected rather than granted.
 */
public record Permission(String value) {

    private static final String COLON = ":";

    /**
     * Creates a Permission from the given string.
     * <p>
     * The value must be non-null, non-empty, and contain exactly one
     * colon separating the resource from the action.
     * Unknown or malformed permissions are rejected.
     *
     * @param value the permission string, must not be null or empty
     * @return a new Permission instance
     * @throws IllegalArgumentException if the value is null, empty,
     *         or does not contain a colon
     */
    public Permission {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Permission value must not be null or empty");
        }
        if (!value.contains(COLON)) {
            throw new IllegalArgumentException(
                    "Permission must follow the resource:action pattern, got: " + value);
        }
    }

    /**
     * Returns the resource part of the permission (before the colon).
     *
     * @return the resource name, never null
     */
    public String resource() {
        int idx = value.indexOf(COLON);
        return value.substring(0, idx);
    }

    /**
     * Returns the action part of the permission (after the colon).
     *
     * @return the action name, never null
     */
    public String action() {
        int idx = value.indexOf(COLON);
        return value.substring(idx + 1);
    }

    /**
     * Checks if this permission matches the given resource and action.
     *
     * @param resource the resource name to compare against
     * @param action   the action name to compare against
     * @return true if both resource and action match
     */
    public boolean matches(String resource, String action) {
        return resource.equals(this.resource()) && action.equals(this.action());
    }

    /**
     * Checks if this permission is for the given resource (any action).
     *
     * @param resource the resource name to compare against
     * @return true if the resource matches
     */
    public boolean forResource(String resource) {
        return resource.equals(this.resource());
    }

    /**
     * Human-readable representation.
     */
    @Override
    public String toString() {
        return value;
    }
}