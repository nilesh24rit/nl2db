package com.nl2db.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/**
 * Repository contract for OrganizationMembership persistence.
 * Owned by nl2db-auth. Provides the basic operations required by the
 * membership and authorization workflow.
 * <p>
 * Extends Spring Data JpaRepository for standard CRUD operations with
 * OrganizationMembership entity.
 * <p>
 * Provides queries for finding memberships by user and organization,
 * and for listing memberships with pagination and role filtering.
 */
@Repository
public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, UUID> {

    /**
     * Find a membership by user ID and organization ID.
     * <p>
     * This is the authoritative lookup for organization membership,
     * used by the permission evaluator. Does not trust client-supplied
     * roles or identities.
     *
     * @param userId  the user identifier
     * @param orgId   the organization identifier
     * @return the membership, or null if not found
     */
    Optional<OrganizationMembership> findByUserIdAndOrganizationId(
            UUID userId, UUID orgId);

    /**
     * Find all active memberships for a given user across all organizations.
     * <p>
     * The query scopes to ACTIVE memberships only, excluding suspended
     * and removed memberships.
     *
     * @param userId the user identifier
     * @return the list of active memberships
     */
    @Query("SELECT m FROM nl2db.organization_memberships m "
            + "WHERE m.userId = :userId "
            + "AND m.membershipStatus = 'ACTIVE'")
    List<OrganizationMembership> findActiveByUserId(UUID userId);

    /**
     * Find an active organization membership for a user.
     * <p>
     * Returns the first active membership found for the given user,
     * or null if the user has no active organization memberships.
     * This is used to derive the trusted tenant context for API key generation.
     *
     * @param userId the user UUID
     * @return the active organization membership, or null
     */
    @Query("SELECT m FROM nl2db.organization_memberships m "
            + "WHERE m.userId = :userId "
            + "AND m.membershipStatus = 'ACTIVE'")
    Optional<OrganizationMembership> findByUserIdAndActiveMembership(UUID userId);

    /**
     * Find all active memberships for a given organization.
     * <p>
     * The query scopes to ACTIVE memberships only.
     *
     * @param orgId the organization identifier
     * @return the list of active memberships
     */
    @Query("SELECT m FROM nl2db.organization_memberships m "
            + "WHERE m.organizationId = :orgId "
            + "AND m.membershipStatus = 'ACTIVE'")
    List<OrganizationMembership> findActiveByOrganizationId(UUID orgId);

    /**
     * Find memberships by role for a given organization.
     * <p>
     * Useful for checking which users have specific roles (e.g., all ADMINs).
     *
     * @param orgId      the organization identifier
     * @param role     the role to filter by (OWNER, ADMIN, MEMBER)
     * @param pageable the pagination and sorting parameters
     * @return the page of memberships with the given role
     */
    @Query("SELECT m FROM nl2db.organization_memberships m "
            + "WHERE m.organizationId = :orgId "
            + "AND m.role = :role "
            + "AND m.membershipStatus = 'ACTIVE'")
    Page<OrganizationMembership> findByOrganizationIdAndRole(
            UUID orgId, String role, PageRequest pageable);
}