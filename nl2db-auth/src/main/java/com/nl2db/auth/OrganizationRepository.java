package com.nl2db.auth;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/**
 * Repository contract for Organization persistence.
 * <p>
 * Owned by nl2db-auth. Provides the basic operations required by the
 * domain at this stage. Extends Spring Data JpaRepository for
 * standard CRUD operations with Organization entity.
 * <p>
 * Do not introduce unnecessary generic repository abstractions.
 * This interface stays focused on Organization-specific queries.
 */
@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    /**
     * Find an organization by its slug.
     *
     * @param slug the URL-friendly slug
     * @return the organization, or null if not found
     */
    Organization findBySlug(String slug);

    /**
     * Check whether a slug already exists in the database.
     *
     * @param slug the slug to check
     * @return true if an organization with this slug already exists
     */
    boolean existsBySlug(String slug);

    /**
     * Check whether a slug already exists, excluding a specific organization.
     *
     * @param slug the slug to check
     * @param excludeId the organization ID to exclude from the check
     * @return true if an organization with this slug already exists (other than the excluded one)
     */
    boolean existsBySlugExcludingCurrent(String slug, UUID excludeId);

    /**
     * Find organizations by owner ID, ordered by creation date (newest first).
     * <p>
     * This query is scoped to the authenticated user only for multi-tenancy.
     *
     * @param ownerId the user ID of the owner
     * @param pageable the pagination and sorting parameters
     * @return the page of organizations owned by the given user
     */
    @Query("SELECT o FROM nl2db.organizations o WHERE o.ownerId = ?1 ORDER BY o.createdAt DESC")
    Page<Organization> findByOwnerIdOrderByCreatedDesc(UUID ownerId, PageRequest pageable);
}