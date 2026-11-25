package com.nl2db.auth;

import com.nl2db.common.ApiKeyUtils;
import com.nl2db.auth.dto.ApiKeyGenerateRequest;
import com.nl2db.auth.dto.ApiKeyGenerateResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * Service for API key generation and management.
 * <p>
 * Responsibilities:
 * - Generate secure API keys with cryptographically random secret material
 * - Persist API key metadata (hash of secret only, never plaintext)
 * - Associate keys with organizations and creators
 * - Provide the complete key only in the one-time creation response
 * <p>
 * Security requirements enforced by this service:
 * - Never persist the complete plaintext API key
 * - Never store the secret in reversible or encrypted form
 * - Only store the SHA-256 hash of the secret
 * - Never log the complete key
 * - Generate keys using SecureRandom (not timestamps, UUIDs alone, or counters)
 */
@Transactional
public class ApiKeyService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Generates a new API key for the authenticated user's active organization.
     * <p>
     * The organization is resolved from the trusted tenant context. The user
     * must have an active membership in that organization.
     * <p>
     * The key generation process:
     * 1. Generate 256 bits (32 bytes) of cryptographically secure random secret
     * 2. Derive an 8-byte public prefix (hex-encoded) from the secret
     * 3. URL-safe base64-encode the full secret
     * 4. Format: nl2db_live_<public-prefix>.<secret>
     * 5. Compute SHA-256 hash of the secret for storage
     * 6. Persist metadata (key ID, organization ID, creator user ID, key name,
     *    public prefix, secret hash, creation timestamp)
     * <p>
     * The complete key is only returned in the creation response. Subsequent
     * retrieval or listing operations must not expose the secret.
     *
     * @param request the generation request containing the key name
     * @param tenantContext the trusted tenant context resolved from authentication
     * @param keyId the internal key identifier (generated if null)
     * @return the API key generation response containing the one-time complete key
     * @throws SecurityException if the user is not authorized for the organization
     */
    public ApiKeyGenerateResponse generateApiKey(ApiKeyGenerateRequest request,
                                                  TenantContext tenantContext,
                                                  UUID keyId) {
        // Validate that the user has an active membership in the organization
        validateOrganizationMembership(tenantContext.organizationId(), tenantContext.principalId());

        // Generate key ID if not provided
        if (keyId == null) {
            keyId = ApiKeyUtils.createKeyId();
        }

        // Generate the API key using cryptographically secure random number generator
        String apiKey = ApiKeyUtils.generateApiKey();

        // Derive the public prefix from the generated key
        String publicPrefix = ApiKeyUtils.getPublicPrefix(apiKey);

        // Compute SHA-256 hash of the secret (never store the secret itself)
        String secretHash = ApiKeyUtils.getSecretHash(apiKey);

        // Set creation timestamp
        Instant now = Instant.now();

        // Persist the API key metadata
        ApiKey apiKeyEntity = new ApiKey(
                keyId,
                tenantContext.organizationId(),
                tenantContext.principalId(),
                request.getKeyName(),
                publicPrefix,
                secretHash,
                now);

        entityManager.persist(apiKeyEntity);

        // Return the complete response with the key (one-time only)
        return new ApiKeyGenerateResponse(
                keyId,
                request.getKeyName(),
                publicPrefix,
                apiKey,
                now);
    }

    /**
     * Validates that the user has an active membership in the given organization.
     * <p>
     * Throws a SecurityException if the user is not a member or the membership
     * is not ACTIVE. This enforces organization-level tenancy isolation.
     *
     * @param organizationId the organization to check membership for
     * @param userId the user to check membership for
     * @throws SecurityException if the user lacks active membership
     */
    private void validateOrganizationMembership(UUID organizationId, UUID userId) {
        // Check for active membership in the organization
        var membership = entityManager.createQuery(
                        "SELECT m FROM nl2db.organization_memberships m " +
                                "WHERE m.organizationId = :orgId " +
                                "AND m.userId = :userId " +
                                "AND m.membershipStatus = 'ACTIVE'", ApiKey.class)
                .setParameter("orgId", organizationId)
                .setParameter("userId", userId)
                .getResultList();

        if (membership.isEmpty()) {
            throw new SecurityException(
                    "User " + userId + " has no active membership in organization " + organizationId);
        }
    }
}