package com.nl2db.gateway.auth;

import com.nl2db.auth.*;
import com.nl2db.auth.dto.ApiKeyGenerateRequest;
import com.nl2db.auth.dto.ApiKeyGenerateResponse;
import com.nl2db.security.Permission;
import com.nl2db.security.PermissionEvaluator;
import com.nl2db.security.TenantContext;
import jakarta.validation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

/**
 * REST controller for API key generation with RBAC authorization.
 * <p>
 * All endpoints enforce organization-scoped permissions through the
 * PermissionEvaluator. Role-based access control is centralized and
 * consistent - no inline role comparisons in controllers.
 * <p>
 * Cross-organization access is denied: a user can only generate API keys
 * for organizations where they have an active membership.
 * <p>
 * Owner protection is enforced:
 * - Users can only generate keys for organizations they belong to
 * - User-supplied organization IDs cannot bypass authorization
 * - The creator is automatically associated with the verified organization
 */
@RestController
@RequestMapping("/api/v1/api-keys")
public class ApiKeyController {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final ApiKeyService apiKeyService;
    private final PermissionEvaluator permissionEvaluator;
    private final OrganizationMembershipRepository organizationMembershipRepository;

    public ApiKeyController(ApiKeyService apiKeyService,
                            PermissionEvaluator permissionEvaluator,
                            OrganizationMembershipRepository organizationMembershipRepository) {
        this.apiKeyService = apiKeyService;
        this.permissionEvaluator = permissionEvaluator;
        this.organizationMembershipRepository = organizationMembershipRepository;
    }

    /**
     * Creates a new API key for the authenticated user's active organization.
     * <p>
     * Endpoint is protected - requires valid JWT authentication.
     * The user must have ACTIVE status and an active membership in the
     * requested organization.
     * <p>
     * The organization is resolved from the trusted tenant context, NOT from
     * the request body, query parameters, or arbitrary headers. User-supplied
     * organization IDs are explicitly rejected for authorization purposes.
     * <p>
     * RBAC enforcement: the user must have at least the organization:read
     * permission for the verified organization.
     * <p>
     * The complete API key is only returned in this one-time creation response.
     * Subsequent retrieval or listing operations must not expose the secret.
     *
     * @param request the key generation request containing the human-readable key name
     * @param principal the authenticated user principal from JWT
     * @return the API key generation response with the one-time complete key
     * @status 201 if the API key was successfully created
     * @status 401 if the user is not authenticated
     * @status 403 if the user is not eligible (inactive, no membership, no permission)
     */
    @PostMapping
    public ResponseEntity<ApiKeyGenerateResponse> createApiKey(
            @Valid @RequestBody ApiKeyGenerateRequest request,
            Principal principal) {

        UUID userId = extractUserIdFromPrincipal(principal);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(null);
        }

        // Resolve the user's active organization membership from the database.
        // This is the "trusted tenant context" - user-supplied data is never trusted.
        var membership = organizationMembershipRepository
                .findByUserIdAndActiveMembership(userId);

        if (membership == null) {
            // User has no active organization membership - deny
            log.debug("Tenant access denied: user {} has no active organization membership",
                    userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(null);
        }

        UUID organizationId = membership.getOrganizationId();

        // Now check that the user has organization:read permission for the verified organization
        boolean hasReadPerm = permissionEvaluator.evaluatePermission(
                userId,
                organizationId,
                Permission.organizationRead());

        if (!hasReadPerm) {
            // Deny - either no membership, insufficient role, or organization
            // is not accessible. Return consistent response without leaking
            // whether the organization exists.
            log.debug("Permission denied: user {} lacks organization:read for org {}",
                    userId, organizationId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(null);
        }

        // Resolve tenant context from the verified organization membership
        var tenantContext = new TenantContext(userId, organizationId);

        // Generate the API key (delegates to service layer for persistence)
        ApiKeyGenerateResponse response = apiKeyService.generateApiKey(
                request, tenantContext, null);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * Extracts the user ID from the JWT principal.
     *
     * @param principal the authenticated user principal
     * @return the user UUID, or null if principal is null
     */
    private UUID extractUserIdFromPrincipal(Principal principal) {
        if (principal == null) {
            return null;
        }
        String name = principal.getName();
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}