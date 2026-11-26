package com.nl2db.security;

import java.util.*;
import java.util.stream.*;

/**
 * Centralized role-permission mapping.
 * <p>
 * Maps the three organizational roles (OWNER, ADMIN, MEMBER) to
 * explicit permission sets. Mappings are deterministic and
 * permission evaluation is deny-by-default.
 * <p>
 * This class owns the policy logic. Controllers and services should
 * not contain role name strings or inline permission checks; instead
 * they should delegate to this evaluator.
 */
public final class RolePermissionMapping {

    private RolePermissionMapping() {
        // utility class - no instantiation
    }

    /**
     * The set of all defined permission strings, in the
     * {@code resource:action} format.
     */
    public static final Set<Permission> ALL_PERMISSIONS = Set.of(
            // Organization-level permissions
            new Permission("organization:read"),
            new Permission("organization:update"),
            new Permission("organization:delete"),

            // Member/invitation permissions
            new Permission("member:read"),
            new Permission("member:invite"),
            new Permission("member:update"),
            new Permission("member:remove"),

            // Database connection permissions
            new Permission("database:read"),
            new Permission("database:create"),
            new Permission("database:update"),
            new Permission("database:delete"),

            // Query execution permissions
            new Permission("query:execute"),
            new Permission("query:read"),

            // Semantic model permissions
            new Permission("semantic:read"),
            new Permission("semantic:update"),

            // Billing permissions
            new Permission("billing:read"),
            new Permission("billing:manage")
    );

    /**
     * Returns the full set of defined permissions. This can be used
     * for validation, enumeration, or policy inspection.
     *
     * @return unmodifiable set of all defined permissions
     */
    public static Set<Permission> allPermissions() {
        return Collections.unmodifiableSet(ALL_PERMISSIONS);
    }

    /**
     * Returns the permissions assigned to the OWNER role.
     * OWNER receives all organization-level permissions, full
     * organization management, membership and invitation management,
     * database connection management, query access, semantic
     * configuration management, and billing management.
     *
     * @return unmodifiable set of OWNER permissions
     */
    public static Set<Permission> ownerPermissions() {
        return Set.of(
                // All organization-level permissions
                new Permission("organization:read"),
                new Permission("organization:update"),
                new Permission("organization:delete"),

                // Membership and invitation management
                new Permission("member:read"),
                new Permission("member:invite"),
                new Permission("member:update"),
                new Permission("member:remove"),

                // Database connection management
                new Permission("database:read"),
                new Permission("database:create"),
                new Permission("database:update"),
                new Permission("database:delete"),

                // Query access
                new Permission("query:execute"),
                new Permission("query:read"),

                // Semantic configuration management
                new Permission("semantic:read"),
                new Permission("semantic:update"),

                // Billing management
                new Permission("billing:read"),
                new Permission("billing:manage")
        );
    }

    /**
     * Returns the permissions assigned to the ADMIN role.
     * ADMIN receives organization read, member read, member
     * invitation and ordinary membership management (subject to
     * owner-protection rules), database connection management,
     * query access, and semantic configuration management where
     * permitted.
     * <p>
     * ADMIN does NOT receive:
     * - organization ownership transfer
     * - owner removal or demotion
     * - organization-level billing operations
     * - member management beyond ordinary invitation/role updates
     * - organization deletion
     *
     * @return unmodifiable set of ADMIN permissions
     */
    public static Set<Permission> adminPermissions() {
        return Set.of(
                // Organization read
                new Permission("organization:read"),

                // Member read
                new Permission("member:read"),

                // Member invitation and ordinary membership management
                new Permission("member:invite"),
                new Permission("member:update"),

                // Database connection management
                new Permission("database:read"),
                new Permission("database:create"),
                new Permission("database:update"),

                // Query access
                new Permission("query:execute"),
                new Permission("query:read"),

                // Semantic configuration management where permitted
                new Permission("semantic:read")
                // Note: semantic:update is intentionally omitted for ADMIN
        );
    }

    /**
     * Returns the permissions assigned to the MEMBER role.
     * MEMBER receives organization read, member read, access to
     * permitted database resources, query execution and query history
     * access where authorized.
     * <p>
     * MEMBER does NOT receive:
     * - member management
     - invitation management
     - organization configuration management
     - billing management
     */
    public static Set<Permission> memberPermissions() {
        return Set.of(
                // Organization read
                new Permission("organization:read"),

                // Member read
                new Permission("member:read"),

                // Access to permitted database resources
                new Permission("database:read"),

                // Query execution and query history access where authorized
                new Permission("query:execute"),
                new Permission("query:read")
        );
    }

    /**
     * Evaluates whether the given user with the given role and membership
     * status has the required permission for the given organization.
     * <p>
     * This is the central authorization check. All endpoint controllers
     * and service-layer operations should use this method rather than
     * inline role comparisons.
     * <p>
     * Deny-by-default: if the user's role is unknown, membership is
     * inactive/invalid, or the permission is not mapped for the role,
     * access is denied.
     *
     * @param role        the user's role in the organization (OWNER, ADMIN,
     *                    MEMBER), case-sensitive
     * @param membershipStatus the user's membership status (ACTIVE, SUSPENDED,
     *                    REMOVED), case-sensitive
     * @param orgStatus   the organization's status (ACTIVE, SUSPENDED, DELETED),
     *                    case-sensitive
     * @param permission  the required permission to check
     * @return true if the user is authorized, false if access should be denied
     */
    public static boolean hasPermission(String role,
                                        String membershipStatus,
                                        String orgStatus,
                                        Permission permission) {
        // Deny if role is null or unknown
        if (role == null) {
            return false;
        }

        Set<Permission> rolePermissions;
        switch (role.trim()) {
            case "OWNER":
                rolePermissions = ownerPermissions();
                break;
            case "ADMIN":
                rolePermissions = adminPermissions();
                break;
            case "MEMBER":
                rolePermissions = memberPermissions();
                break;
            default:
                // Unknown role - deny by default
                return false;
        }

        // Deny if membership is not active
        if (!"ACTIVE".equalsIgnoreCase(membershipStatus)) {
            return false;
        }

        // Deny if organization is not accessible
        if (!"ACTIVE".equalsIgnoreCase(orgStatus)) {
            return false;
        }

        // Check if the permission is in the role's permission set
        return rolePermissions.contains(permission);
    }

    /**
     * Convenience overload that uses enum-style status strings.
     */
    public static boolean hasPermission(Role role,
                                        MembershipStatus membershipStatus,
                                        OrganizationStatus organizationStatus,
                                        Permission permission) {
        return hasPermission(
                role != null ? role.value() : null,
                membershipStatus != null ? membershipStatus.value() : null,
                organizationStatus != null ? organizationStatus.value() : null,
                permission);
    }

    /**
     * Enum representing the three organizational roles.
     */
    public enum Role {
        OWNER("OWNER"),
        ADMIN("ADMIN"),
        MEMBER("MEMBER");

        private final String value;

        Role(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    /**
     * Enum representing membership status.
     */
    public enum MembershipStatus {
        ACTIVE("active"),
        SUSPENDED("suspended"),
        REMOVED("removed");

        private final String value;

        MembershipStatus(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    /**
     * Enum representing organization status.
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
    }
}