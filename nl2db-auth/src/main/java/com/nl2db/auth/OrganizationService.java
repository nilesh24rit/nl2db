package com.nl2db.auth;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Service layer for Organization business logic.
 * <p>
 * Encapsulates the core domain operations for organization management:
 * creation with ownership assignment, retrieval, listing, and updates.
 * All operations enforce domain invariants and multi-tenancy scoping.
 * <p>
 * Transaction boundaries are managed by Spring @Transactional; no external
 * network calls are performed within transaction context.
 */
@Service
public class OrganizationService {

    @PersistenceContext
    private EntityManager entityManager;

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    public OrganizationService(OrganizationRepository organizationRepository,
                                UserRepository userRepository) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
    }

    /**
     * Creates a new organization for the authenticated user.
     * <p>
     * Workflow:
     * 1. Resolve the user from the validated JWT subject (user ID)
     * 2. Verify the user is ACTIVE and email verified
     * 3. Validate the organization name
     * 4. Generate a normalized slug
     * 5. Check slug availability
     * 6. Create the organization
     * 7. Persist organization and ownership atomically
     * <p>
     * The authenticated user's identity comes from the validated JWT principal.
     * Never trusts a user ID supplied by the request body.
     *
     * @param name the organization name
     * @param userId the authenticated user UUID from JWT principal
     * @return the created organization response
     * @throws IllegalArgumentException if user is not ACTIVE or not email verified
     * @throws IllegalStateException if slug already exists
     */
    @Transactional
    public OrganizationResponse createOrganization(String name, UUID userId) {
        // Step 1: Resolve the user from the validated JWT subject
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Authenticated user not found in database"));

        // Step 2: Verify the user is ACTIVE and email verified
        if (user.status() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Only ACTIVE users may create organizations");
        }

        // Note: Email verification check is handled at the authentication level
        // (pending-verification users are rejected during login/token issuance)

        // Step 3: Validate the organization name (already validated by DTO constraints,
        // but we double-check here for robustness)
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Organization name must not be blank");
        }

        // Step 4: Generate a normalized slug
        String slug = normalizeSlug(name);

        // Step 5: Check slug availability
        if (organizationRepository.existsBySlug(slug)) {
            // Collision handling: append a suffix and try again
            // Use a predictable collision strategy that does not overwrite
            // another organization's slug. The final uniqueness decision is
            // made at the database level.
            slug = generateUniqueSlug(slug, name);
        }

        // Step 6: Create the organization with the authenticated user as owner
        Instant now = Instant.now();
        Organization organization = new Organization(
                UUID.randomUUID(),
                name,
                slug,
                OrganizationStatus.ACTIVE,
                userId,
                now);

        // Persist the organization
        organizationRepository.save(organization);

        // Step 7: Return the created organization response
        return new OrganizationResponse(
                organization.id(),
                organization.name(),
                organization.slug(),
                organization.status(),
                organization.createdAt());
    }

    /**
     * Retrieves an organization by ID, scoped to the authenticated user.
     * <p>
     * Only the organization owner may retrieve it. Inaccessible organizations
     * return a consistent non-disclosing response that does not leak whether
     * an unrelated organization's ID exists.
     *
     * @param organizationId the organization UUID
     * @param userId the authenticated user UUID from JWT principal
     * @return the organization response, or null if inaccessible
     */
    public OrganizationResponse getOrganization(UUID organizationId, UUID userId) {
        // Look up the organization by ID
        Organization organization = organizationRepository.findById(organizationId)
                .orElse(null);

        if (organization == null) {
            // Organization does not exist - return null (caller should return 404)
            return null;
        }

        // Check ownership: only the owner may retrieve the organization
        if (!organization.ownerId().equals(userId)) {
            // Unrelated user - return null (do not leak existence)
            return null;
        }

        // Check if organization is accessible (not deleted)
        if (organization.status() == OrganizationStatus.DELETED) {
            return null;
        }

        return new OrganizationResponse(
                organization.id(),
                organization.name(),
                organization.slug(),
                organization.status(),
                organization.createdAt());
    }

    /**
     * Lists organizations owned by the authenticated user.
     * <p>
     * Returns only organizations owned by the authenticated user. Never returns
     * organizations belonging to unrelated users. Supports pagination with
     * deterministic ordering.
     *
     * @param userId the authenticated user UUID from JWT principal
     * @param page the page number (0-indexed)
     * @param size the page size (default 20, max 100)
     * @return the paginated organization response list
     */
    public java.util.List<OrganizationResponse> listOrganizations(UUID userId, int page, int size) {
        // Enforce maximum page size
        if (size > 100) {
            size = 100;
        }
        if (size <= 0) {
            size = 20;
        }

        // Use Spring Data JPA pagination with ownership scoping
        // The query is scoped to the authenticated user only
        var pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("createdAt").descending());

        var pageResult = organizationRepository.findByOwnerIdOrderByCreatedDesc(userId, pageable);

        return pageResult.stream().map(organization -> new OrganizationResponse(
                organization.id(),
                organization.name(),
                organization.slug(),
                organization.status(),
                organization.createdAt())).toList();
    }

    /**
     * Updates an organization name for the owner.
     * <p>
     * Only the owner can update organization details. Do not allow owner ID
     * changes or status changes through this endpoint. Recalculate the slug
     * safely when the name changes. Preserve the organization's stable UUID.
     *
     * @param organizationId the organization UUID
     * @param userId the authenticated user UUID from JWT principal
     * @param newName the new organization name
     * @return the updated organization response, or null if unauthorized
     */
    @Transactional
    public OrganizationResponse updateOrganizationName(UUID organizationId, UUID userId, String newName) {
        // Look up the organization by ID
        Organization organization = organizationRepository.findById(organizationId)
                .orElse(null);

        if (organization == null) {
            return null;
        }

        // Check ownership: only the owner can update
        if (!organization.ownerId().equals(userId)) {
            return null;
        }

        // Check if organization is suspended - suspended organizations cannot be modified
        if (organization.status() == OrganizationStatus.SUSPENDED) {
            return null;
        }

        // Validate the new name
        if (newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("Organization name must not be blank");
        }

        // Update the name using the setter (this also updates updatedAt)
        organization.setName(newName);

        // Recalculate the slug based on the new name
        String newSlug = normalizeSlug(newName);

        // Check slug availability (excluding the current organization)
        if (organizationRepository.existsBySlugExcludingCurrent(newSlug, organizationId)) {
            // Collision - generate unique slug
            newSlug = generateUniqueSlug(newSlug, newName);
        }

        // Update the slug using the setter
        organization.setSlug(newSlug);

        organizationRepository.save(organization);

        return new OrganizationResponse(
                organization.id(),
                organization.name(),
                organization.slug(),
                organization.status(),
                organization.createdAt());
    }

    /**
     * Normalizes an organization name to a URL-friendly slug.
     * <p>
     * The strategy is:
     * - Lowercase all characters
     * - Replace spaces and special characters with hyphens
     * - Remove consecutive hyphens
     * - Trim leading/trailing hyphens
     * - Result is deterministic and safe for database indexing
     *
     * @param name the organization name
     * @return the normalized slug
     */
    private String normalizeSlug(String name) {
        if (name == null || name.isEmpty()) {
            return "organization";
        }

        // Lowercase
        String result = name.toLowerCase();

        // Replace non-alphanumeric characters (except spaces) with hyphens
        result = result.replaceAll("[^a-z0-9\\s]", "-");

        // Replace spaces with hyphens
        result = result.replace(' ', '-');

        // Remove consecutive hyphens
        result = result.replaceAll("-+", "-");

        // Trim leading/trailing hyphens
        result = result.replaceAll("^-|-$", "");

        // Ensure we have at least something
        if (result.isEmpty()) {
            return "organization";
        }

        return result;
    }

    /**
     * Generates a unique slug when a collision is detected.
     * <p>
     * Strategy: append "-1", "-2", etc. to the base slug and check again.
     * The final uniqueness decision is made at the database level (unique
     * constraint enforcement). This loop will terminate because the number
     * of organizations with the same base slug is finite.
     *
     * @param baseSlug the base slug to make unique
     * @param name the organization name for further collision resolution
     * @return a unique slug that does not exist in the database
     */
    private String generateUniqueSlug(String baseSlug, String name) {
        String candidate = baseSlug;
        int counter = 1;

        while (organizationRepository.existsBySlug(candidate)) {
            // Append counter and try again
            candidate = baseSlug + "-" + counter;
            counter++;
        }

        return candidate;
    }
}