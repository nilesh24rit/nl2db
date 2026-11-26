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
 * Repository contract for OrganizationInvitation persistence.
 * Owned by nl2db-auth. Provides the basic operations required by the
 * invitation and membership workflow.
 * <p>
 * Extends Spring Data JpaRepository for standard CRUD operations with
 * OrganizationInvitation entity.
 * <p>
 * Provides queries for finding invitations by token hash, organization,
 * and status. Does not trust client-supplied tokens or identities.
 */
@Repository
public interface OrganizationInvitationRepository extends JpaRepository<OrganizationInvitation, UUID> {

    /**
     * Find an invitation by its token hash.
     * <p>
     * This is the authoritative lookup for invitation validation,
     * used during invitation acceptance. Does not trust client-supplied
     * token values beyond the hash.
     *
     * @param tokenHash the SHA-256 hash of the invitation token
     * @return the invitation, or null if not found
     */
    Optional<OrganizationInvitation> findByTokenHash(String tokenHash);

    /**
     * Find pending invitations for a given organization.
     * <p>
     * The query scopes to PENDING invitations only, excluding accepted,
     * revoked, and expired invitations.
     *
     * @param orgId the organization identifier
     * @return the list of pending invitations
     */
    @Query("SELECT i FROM nl2db.organization_invitations i "
            + "WHERE i.organizationId = :orgId "
            + "AND i.status = 'PENDING'")
    List<OrganizationInvitation> findPendingByOrganizationId(UUID orgId);

    /**
     * Find invitations by email for a given organization.
     * <p>
     * Useful for checking whether an email has an pending invitation
     * in a specific organization.
     *
     * @param orgId    the organization identifier
     * @param email    the email address to search for
     * @return the list of invitations matching the email
     */
    @Query("SELECT i FROM nl2db.organization_invitations i "
            + "WHERE i.organizationId = :orgId "
            + "AND i.email = :email")
    List<OrganizationInvitation> findByOrganizationIdAndEmail(
            UUID orgId, String email);

    /**
     * Find an invitation by organization and email, with PENDING status.
     * <p>
     * Convenience method that combines organization scoping and email
     * matching with status filtering.
     *
     * @param orgId    the organization identifier
     * @param email    the email address to search for
     * @return the pending invitation, or null if not found
     */
    @Query("SELECT i FROM nl2db.organization_invitations i "
            + "WHERE i.organizationId = :orgId "
            + "AND i.email = :email "
            + "AND i.status = 'PENDING'")
    Optional<OrganizationInvitation> findPendingByOrganizationIdAndEmail(
            UUID orgId, String email);

    /**
     * Find expired invitations for a given organization.
     * <p>
     * The query scopes to EXPIRED invitations only.
     *
     * @param orgId the organization identifier
     * @return the list of expired invitations
     */
    @Query("SELECT i FROM nl2db.organization_invitations i "
            + "WHERE i.organizationId = :orgId "
            + "AND i.status = 'EXPIRED'")
    List<OrganizationInvitation> findExpiredByOrganizationId(UUID orgId);

    /**
     * Find revoked invitations for a given organization.
     * <p>
     * The query scopes to REVOKED invitations only.
     *
     * @param orgId the organization identifier
     * @return the list of revoked invitations
     */
    @Query("SELECT i FROM nl2db.organization_invitations i "
            + "WHERE i.organizationId = :orgId "
            + "AND i.status = 'REVOKED'")
    List<OrganizationInvitation> findRevokedByOrganizationId(UUID orgId);
}