package com.nl2db.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/**
 * Repository contract for API key persistence.
 * <p>
 * Owned by nl2db-auth. Provides the basic operations required by the
 * domain at this stage. Extends Spring Data JpaRepository for
 * standard CRUD operations with ApiKey entity.
 * <p>
 * Do not introduce unnecessary generic repository abstractions.
 * This interface stays focused on API key-specific queries.
 */
@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    /**
     * Find an API key by its public prefix.
     * The public prefix is used for key identification without revealing the secret.
     *
     * @param publicPrefix the public prefix
     * @return the API key, or null if not found
     */
    Optional<ApiKey> findByPublicPrefix(String publicPrefix);

    /**
     * Check whether a public prefix already exists in the database.
     *
     * @param publicPrefix the public prefix to check
     * @return true if an API key with this public prefix already exists
     */
    boolean existsByPublicPrefix(String publicPrefix);

    /**
     * Find API keys by organization ID.
     *
     * @param organizationId the organization UUID
     * @param pageable the pagination and sorting parameters
     * @return the page of API keys for the given organization
     */
    @Query("SELECT a FROM nl2db.api_keys a " +
            "WHERE a.organizationId = ?1 ORDER BY a.createdAt DESC")
    Page<ApiKey> findByOrganizationIdOrderByCreatedDesc(UUID organizationId, PageRequest pageable);

    /**
     * Find an API key by organization ID and key ID, scoped to the authenticated user.
     * This ensures cross-tenant isolation - a user can only retrieve their own keys.
     *
     * @param organizationId the organization UUID
     * @param keyId the API key UUID
     * @param userId the user UUID
     * @return the API key, or null if not found or access denied
     */
    @Query("SELECT a FROM nl2db.api_keys a " +
            "WHERE a.organizationId = ?1 " +
            "AND a.id = ?2 " +
            "AND a.creatorUserId = ?3")
    Optional<ApiKey> findByOrganizationIdAndKeyIdAndCreator(UUID organizationId, UUID keyId, UUID userId);

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
}