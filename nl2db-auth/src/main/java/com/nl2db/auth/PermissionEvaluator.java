package com.nl2db.auth;

import com.nl2db.security.Permission;
import com.nl2db.security.RolePermissionMapping;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.Optional;

/**
 * Centralized permission evaluator for RBAC authorization.
 * <p>
 * Responsibilities:
 * - Resolve organization membership from PostgreSQL using the
 *   authorized repositories
 * - Evaluate whether a user has a given permission for an organization
 * - Support organization-scoped authorization
 * - Support resource ownership checks
 * - Deny by default for unknown roles, missing memberships, and
 *   invalid states
 * - Reject suspended/deleted organizations
 * - Never trust client-supplied roles or identities
 * <p>
 * This service is the single point of authorization decision logic.
 * All controller and service-layer operations should consult this
 * evaluator rather than inline role comparisons.
 */
@Service
public class PermissionEvaluator {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationInvitationRepository invitationRepository;

    /**
     * Constructs a PermissionEvaluator with the required repositories.
     * All dependencies are Spring-managed repositories that provide
     * authoritative PostgreSQL-backed membership resolution.
     */
    public PermissionEvaluator(OrganizationRepository organizationRepository,
                               OrganizationMembershipRepository membershipRepository,
                               OrganizationInvitationRepository invitationRepository) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.invitationRepository = invitationRepository;
    }

    /**
     * Evaluates whether the authenticated user has the required permission
     * for the given organization.
     * <p>
     * Workflow:
     * 1. Look up the organization by ID from the database
     * 2. If organization not found or deleted, deny
     * 3. Look up the user's membership for this organization
     * 4. If membership not found, inactive, or removed, deny
     * 5. Evaluate the permission using the role-permission mapping
     * 6. Return the authorization decision
     * <p>
     * Deny-by-default: missing membership, missing organization, or
     * unrecognized permission results in denial.
     *
     * @param userId          the authenticated user UUID from JWT principal
     * @param organizationId  the organization UUID being accessed
     * @param permission      the required permission to check
     * @return true if authorized, false if denied
     */
    @Transactional(readOnly = true)
    public boolean evaluatePermission(UUID userId, UUID organizationId,
                                       Permission permission) {
        // Step 1: Look up the organization
        var organization = organizationRepository.findById(organizationId)
                .orElse(null);

        if (organization == null) {
            // Organization not found - deny (do not leak existence)
            return false;
        }

        // Step 2: Check organization status
        if (organization.status() == OrganizationStatus.DELETED) {
            // Deleted organization - deny
            return false;
        }

        if (organization.status() == OrganizationStatus.SUSPENDED) {
            // Suspended organization - deny
            return false;
        }

        // Step 3: Look up the user's membership for this organization
        var membershipOpt = membershipRepository
                .findByUserIdAndOrganizationId(userId, organizationId);

        if (membershipOpt.isEmpty()) {
            // No membership found - deny (user is not a member)
            return false;
        }

        var membership = membershipOpt.get();

        // Step 4: Check membership status
        if ("REMOVED".equalsIgnoreCase(membership.membershipStatus())) {
            // Removed membership - deny
            return false;
        }

        if ("SUSPENDED".equalsIgnoreCase(membership.membershipStatus())) {
            // Suspended membership - deny
            return false;
        }

        if (!"ACTIVE".equalsIgnoreCase(membership.membershipStatus())) {
            // Unknown membership status - deny by default
            return false;
        }

        // Step 5: Evaluate permission using the role-permission mapping
        return RolePermissionMapping.hasPermission(
                membership.role(),
                membership.membershipStatus(),
                organization.status().name(),
                permission
        );
    }

    /**
     * Evaluates whether the authenticated user has the required permission
     * for the given organization, with the user entity fully loaded.
     *
     * @param user            the authenticated user entity
     * @param organizationId  the organization UUID being accessed
     * @param permission      the required permission to check
     * @return true if authorized, false if denied
     */
    @Transactional(readOnly = true)
    public boolean evaluatePermission(User user, UUID organizationId,
                                       Permission permission) {
        // Step 1: Look up the organization
        var organization = organizationRepository.findById(organizationId)
                .orElse(null);

        if (organization == null) {
            return false;
        }

        // Step 2: Check organization status
        if (organization.status() == OrganizationStatus.DELETED) {
            return false;
        }

        if (organization.status() == OrganizationStatus.SUSPENDED) {
            return false;
        }

        // Step 3: Look up the user's membership for this organization
        if (user == null || user.id() == null) {
            return false;
        }

        var membershipOpt = membershipRepository
                .findByUserIdAndOrganizationId(user.id(), organizationId);

        if (membershipOpt.isEmpty()) {
            return false;
        }

        var membership = membershipOpt.get();

        // Step 4: Check membership status
        if ("REMOVED".equalsIgnoreCase(membership.membershipStatus())) {
            return false;
        }

        if ("SUSPENDED".equalsIgnoreCase(membership.membershipStatus())) {
            return false;
        }

        if (!"ACTIVE".equalsIgnoreCase(membership.membershipStatus())) {
            return false;
        }

        // Step 5: Evaluate permission using the role-permission mapping
        return RolePermissionMapping.hasPermission(
                membership.role(),
                membership.membershipStatus(),
                organization.status().name(),
                permission);
    }

    /**
     * Checks if the authenticated user is the owner of the given organization.
     *
     * @param userId      the authenticated user UUID from JWT principal
     * @param organizationId the organization UUID being checked
     * @return true if the user is the owner, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isOwner(UUID userId, UUID organizationId) {
        // Look up the organization
        var organization = organizationRepository.findById(organizationId)
                .orElse(null);

        if (organization == null) {
            return false;
        }

        // Check if the user is the database owner
        if (organization.ownerId() == null) {
            return false;
        }

        if (userId == null || !userId.equals(organization.ownerId())) {
            return false;
        }

        // Also check membership role is OWNER
        var membershipOpt = membershipRepository
                .findByUserIdAndOrganizationId(userId, organizationId);

        if (membershipOpt.isEmpty()) {
            // User is the database owner but has no membership record?
            // Treat as not owner for safety
            return userId.equals(organization.ownerId());
        }

        var membership = membershipOpt.get();

        // User must have OWNER role in membership AND be the database owner
        return userId.equals(organization.ownerId())
                && "OWNER".equalsIgnoreCase(membership.role());
    }

    /**
     * Checks if the authenticated user can perform owner-only operations
     * on the given organization.
     *
     * @param userId the authenticated user UUID from JWT principal
     * @param organizationId the organization UUID being checked
     * @return true if the user CAN perform owner-only operations (i.e., they
     *         are the owner), false if they cannot (i.e., they are not the owner)
     */
    @Transactional(readOnly = true)
    public boolean canPerformOwnerOperation(UUID userId, UUID organizationId) {
        return isOwner(userId, organizationId);
    }

    /**
     * Checks if the authenticated user is a member of the given organization
     * with at least the minimum required role.
     *
     * @param userId the authenticated user UUID from JWT principal
     * @param organizationId the organization UUID being checked
     * @param minimumRole the minimum role required (OWNER is highest,
     *                    ADMIN is middle, MEMBER is lowest)
     * @return true if the user has at least the minimum role in the organization
     */
    @Transactional(readOnly = true)
    public boolean hasAtLeastRole(UUID userId, UUID organizationId,
                                   String minimumRole) {
        if (userId == null) {
            return false;
        }

        var membershipOpt = membershipRepository
                .findByUserIdAndOrganizationId(userId, organizationId);

        if (membershipOpt.isEmpty()) {
            return false;
        }

        var membership = membershipOpt.get();
        String role = membership.role();

        // OWNER > ADMIN > MEMBER
        return switch (minimumRole) {
            case "OWNER" -> "OWNER".equals(role);
            case "ADMIN" -> "ADMIN".equals(role) || "OWNER".equals(role);
            case "MEMBER" -> "MEMBER".equals(role) || "ADMIN".equals(role) || "OWNER".equals(role);
            default -> false;
        };
    }

    /**
     * Retrieves the user's membership for a given organization, or empty
     * if the user has no membership (inactive, removed, or not a member).
     * This is an authoritative lookup from PostgreSQL, not a JWT claim.
     *
     * @param userId the authenticated user UUID from JWT principal
     * @param orgId  the organization UUID
     * @return optional membership, empty if no active membership exists
     */
    @Transactional(readOnly = true)
    public Optional<OrganizationMembership> getMembership(UUID userId, UUID orgId) {
        if (userId == null) {
            return Optional.empty();
        }
        return membershipRepository.findByUserIdAndOrganizationId(userId, orgId);
    }

    /**
     * Checks if a given permission string is a valid, defined permission.
     * Unknown permissions are always denied.
     *
     * @param permissionStr the permission string to validate (resource:action format)
     * @return true if the permission is defined in the role-permission mapping
     */
    public static boolean isValidPermission(String permissionStr) {
        if (permissionStr == null || permissionStr.trim().isEmpty()) {
            return false;
        }
        return Arrays.stream(RolePermissionMapping.ALL_PERMISSIONS
                .toArray(new Permission[0]))
                .anyMatch(p -> p.value().equals(permissionStr));
    }
}