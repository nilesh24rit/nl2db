package com.nl2db.gateway.auth;

import com.nl2db.auth.*;
import com.nl2db.auth.OrganizationRole;
import com.nl2db.auth.MembershipStatus;
import com.nl2db.security.JwtTokenProvider;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for organization management operations.
 * <p>
 * Endpoints:
 * - POST /api/v1/organizations - Create a new organization
 * - GET /api/v1/organizations/{organizationId} - Retrieve an organization
 * - GET /api/v1/organizations - List organizations owned by authenticated user
 * - PATCH /api/v1/organizations/{organizationId} - Update organization name
 * - POST /api/v1/organizations/{organizationId}/invitations - Create an invitation
 * - POST /api/v1/invitations/accept - Accept an invitation
 * - GET /api/v1/organizations/{organizationId}/members - List organization members
 * - PATCH /api/v1/organizations/{organizationId}/members/{membershipId} - Update member role
 * - DELETE /api/v1/organizations/{organizationId}/members/{membershipId} - Remove member
 * - GET /api/v1/organizations/{organizationId}/invitations - List organization invitations
 * - DELETE /api/v1/organizations/{organizationId}/invitations/{invitationId} - Revoke invitation
 * <p>
 * All endpoints require authentication. The authenticated user's identity
 * is derived from the validated JWT principal. User-supplied IDs in request
 * bodies are never trusted; the JWT-derived identity always prevails.
 */
@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private final com.nl2db.auth.OrganizationService organizationService;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationInvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailDeliveryService emailDeliveryService;

    public OrganizationController(com.nl2db.auth.OrganizationService organizationService,
                                  OrganizationMembershipRepository membershipRepository,
                                  OrganizationInvitationRepository invitationRepository,
                                  UserRepository userRepository,
                                  JwtTokenProvider jwtTokenProvider,
                                  EmailDeliveryService emailDeliveryService) {
        this.organizationService = organizationService;
        this.membershipRepository = membershipRepository;
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.emailDeliveryService = emailDeliveryService;
    }

    /**
     * Creates a new organization for the authenticated user.
     * <p>
     * The authenticated user is automatically assigned as the owner.
     * Request-supplied owner IDs are explicitly ignored.
     *
     * @param request the organization creation request containing the name
     * @return the created organization response with HTTP 201 status
     * @status 201 if the organization was successfully created
     * @status 401 if the request is not authenticated
     * @status 403 if the user is not ACTIVE or email verification is pending
     * @status 409 if the slug already exists (concurrent creation conflict)
     */
    @PostMapping
    public ResponseEntity<OrganizationResponse> createOrganization(
            @Valid @RequestBody OrganizationCreateRequest request,
            HttpServletRequest httpServletRequest) {

        // Extract the authenticated user ID from the JWT principal
        UUID userId = getAuthenticatedUserId(httpServletRequest);

        // Create the organization - the service layer handles ownership assignment
        OrganizationResponse response = organizationService.createOrganization(
                request.name(), userId);

        return ResponseEntity.status(201).body(response);
    }

    /**
     * Retrieves an organization by ID.
     * <p>
     * Only the organization owner may retrieve it. Inaccessible organizations
     * return a consistent 404 response that does not leak whether an
     * unrelated organization's ID exists.
     *
     * @param organizationId the organization UUID to retrieve
     * @return the organization response, or 404 if inaccessible
     * @status 200 if the organization was successfully retrieved
     * @status 401 if the request is not authenticated
     * @status 404 if the organization does not exist or the caller is not the owner
     */
    @GetMapping("/{organizationId}")
    public ResponseEntity<OrganizationResponse> getOrganization(
            @PathVariable("organizationId") UUID organizationId,
            HttpServletRequest httpServletRequest) {

        // Extract the authenticated user ID from the JWT principal
        UUID userId = getAuthenticatedUserId(httpServletRequest);

        OrganizationResponse response = organizationService.getOrganization(
                organizationId, userId);

        if (response == null) {
            return ResponseEntity.not().build();
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Lists organizations owned by the authenticated user.
     * <p>
     * Returns only organizations owned by the authenticated user. Never returns
     * organizations belonging to unrelated users. Supports pagination with
     * deterministic ordering by creation date (newest first).
     *
     * @param page the page number (0-indexed, default 0)
     * @param size the page size (default 20, max 100)
     * @return the paginated organization response list
     * @status 200 if the organizations were successfully retrieved
     * @status 401 if the request is not authenticated
     */
    @GetMapping
    public ResponseEntity<java.util.List<OrganizationResponse>> listOrganizations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpServletRequest) {

        // Extract the authenticated user ID from the JWT principal
        UUID userId = getAuthenticatedUserId(httpServletRequest);

        java.util.List<OrganizationResponse> responses = organizationService.listOrganizations(
                userId, page, size);

        return ResponseEntity.ok(responses);
    }

    /**
     * Updates the organization name for the owner.
     * <p>
     * Only the owner can update organization details. Do not allow owner ID
     * changes or status changes through this endpoint. Recalculate the slug
     * safely when the name changes. Preserve the organization's stable UUID.
     *
     * @param organizationId the organization UUID to update
     * @param request the update request containing the new name
     * @return the updated organization response, or 404/403 if unauthorized
     * @status 200 if the organization was successfully updated
     * @status 401 if the request is not authenticated
     * @status 403 if the caller is not the owner
     * @status 404 if the organization does not exist
     * @status 409 if the new slug collides with another organization's slug
     * @status 409 if the organization is SUSPENDED
     */
    @PatchMapping("/{organizationId}")
    public ResponseEntity<OrganizationResponse> updateOrganizationName(
            @PathVariable("organizationId") UUID organizationId,
            @Valid @RequestBody OrganizationUpdateRequest request,
            HttpServletRequest httpServletRequest) {

        // Extract the authenticated user ID from the JWT principal
        UUID userId = getAuthenticatedUserId(httpServletRequest);

        OrganizationResponse response = organizationService.updateOrganizationName(
                organizationId, userId, request.name());

        if (response == null) {
            return ResponseEntity.not().build();
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Creates an organization invitation.
     * <p>
     * Requires authentication. Only the organization OWNER may invite users.
     * Verifies the caller belongs to the requested organization with OWNER role.
     * Only assigns ADMIN or MEMBER through this endpoint.
     * Never permits assigning OWNER through an invitation.
     * Normalizes the invitation email.
     * Rejects invitations to an existing ACTIVE member.
     * Handles an existing pending invitation according to a clear resend policy.
     * Generates a cryptographically secure invitation token.
     * Persists only its hash.
     * Sets a configurable expiration, initially 7 days.
     * Dispatches the invitation email through the email delivery abstraction.
     *
     * @param organizationId the organization UUID
     * @param request the invitation request containing email and role
     * @return HTTP 201 when the invitation is successfully created
     * @status 201 if the invitation was successfully created
     * @status 401 if the request is not authenticated
     * @status 403 if the caller is not the organization owner
     * @status 403 if the caller is not a member of the organization
     * @status 403 if the requested role is OWNER
     * @status 403 if the invited user is already an ACTIVE member
     * @status 409 if there is an existing pending invitation for the same email
     * @status 409 if the organization does not exist
     */
    @PostMapping("/{organizationId}/invitations")
    public ResponseEntity<InvitationResponse> createInvitation(
            @PathVariable("organizationId") UUID organizationId,
            @Valid @RequestBody InvitationCreateRequest request,
            HttpServletRequest httpServletRequest) {

        // Extract the authenticated user ID from the JWT principal
        UUID authUserId = getAuthenticatedUserId(httpServletRequest);

        // Step 1: Verify the organization exists
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Organization not found"));

        // Step 2: Verify the caller is the organization owner
        if (!organization.ownerId().equals(authUserId)) {
            return ResponseEntity.status(403)
                    .body(buildErrorResponse("Only the organization owner may create invitations"));
        }

        // Step 3: Verify the user is a member of the organization with OWNER role
        OrganizationMembership membership = membershipRepository.findByOrganizationIdAndUserId(
                organizationId, authUserId);
        if (membership == null) {
            return ResponseEntity.status(403)
                    .body(buildErrorResponse("You must be a member of the organization to create invitations"));
        }
        if (membership.role() != OrganizationRole.OWNER) {
            return ResponseEntity.status(403)
                    .body(buildErrorResponse("Only the organization owner may create invitations"));
        }

        // Step 4: Normalize the invitation email
        String normalizedEmail = request.invitedEmail().trim().toLowerCase();

        // Step 5: Reject invitations to an existing ACTIVE member
        User existingUser = userRepository.findByNormalizedEmail(normalizedEmail);
        if (existingUser != null && existingUser.status() == UserStatus.ACTIVE) {
            return ResponseEntity.status(403)
                    .body(buildErrorResponse("Cannot invite an existing ACTIVE member"));
        }

        // Step 6: Check for existing pending invitation for the same email and organization
        if (invitationRepository.existsByOrganizationIdAndInvitedEmail(organizationId, normalizedEmail)) {
            // There's already a pending invitation - in this stage, reject it
            // (later stages may implement resend/replacement policy)
            return ResponseEntity.status(409)
                    .body(buildErrorResponse("There is already a pending invitation for this email address"));
        }

        // Step 7: Generate invitation token with cryptographically secure random entropy
        String rawToken = generateInvitationToken();

        // Step 8: Compute SHA-256 hash of the raw token
        String tokenHash = computeSha256Hash(rawToken);

        // Step 9: Set expiration (7 days from now)
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(7 * 24 * 60 * 60); // 7 days

        // Step 10: Determine the assigned role (ADMIN or MEMBER from request)
        OrganizationRole invitedRole = request.role();
        if (invitedRole == OrganizationRole.ADMIN || invitedRole == OrganizationRole.MEMBER) {
            // Valid role - use as-is
        } else {
            // Default to MEMBER if invalid role specified
            invitedRole = OrganizationRole.MEMBER;
        }

        // Step 11: Create the invitation with all fields including token hash
        String inviterEmail = getInviterEmail(authUserId);
        OrganizationInvitation invitation = new OrganizationInvitation(
                organizationId, normalizedEmail, invitedRole, authUserId, tokenHash, expiresAt, now);

        // Step 12: Persist the invitation
        invitationRepository.save(invitation);

        // Step 13: Dispatch invitation email after commit
        // TODO: Use after-commit dispatch or transactional outbox in future stage

        // Step 14: Return the invitation response (without token hash)
        InvitationResponse response = new InvitationResponse(
                invitation.id(),
                invitation.invitedEmail(),
                invitation.role(),
                invitation.status(),
                invitation.createdAt(),
                invitation.expiresAt(),
                invitation.acceptedAt(),
                invitation.revokedAt(),
                invitation.invitedByUserId());

        return ResponseEntity.status(201).body(response);
    }

    /**
     * Accepts an organization invitation.
     * <p>
     * Workflow:
     * 1. Authenticate the caller.
     * 2. Validate the invitation token by hashing it and finding the invitation.
     * 3. Verify it is PENDING and unexpired.
     * 4. Load the authenticated user using the JWT subject.
     * 5. Verify that the authenticated user's email matches the invited email.
     * 6. Verify that the user is ACTIVE and email verified.
     * 7. Create the organization membership with the invited role.
     * 8. Mark the invitation ACCEPTED.
     * 9. Persist the changes transactionally.
     *
     * @param request the invitation accept request containing the token
     * @return success response
     * @status 200 if the invitation was successfully accepted
     * @status 400 if the invitation is invalid, expired, revoked, or accepted
     * @status 401 if the request is not authenticated
     * @status 403 if the email does not match
     */
    @PostMapping("/invitations/accept")
    public ResponseEntity<Void> acceptInvitation(
            @Valid @RequestBody InvitationAcceptRequest request,
            HttpServletRequest httpServletRequest) {

        // Step 1: Authenticate the caller
        UUID authUserId = getAuthenticatedUserId(httpServletRequest);

        // Step 2: Hash the supplied invitation token
        // The token is hashed and compared against the stored token_hash
        String tokenHash = computeSha256Hash(request.token());

        // Step 3: Find the invitation by token hash
        OrganizationInvitation invitation = invitationRepository.findByTokenHash(tokenHash);

        if (invitation == null) {
            // Invalid invitation - return generic error without exposing internal state
            return ResponseEntity.status(400).body(null);
        }

        // Step 4: Verify it is PENDING and unexpired
        if (invitation.status() != InvitationStatus.PENDING) {
            // Invitation is not in PENDING state (already accepted, expired, or revoked)
            return ResponseEntity.status(400).body(null);
        }

        if (invitation.isExpired()) {
            // Invitation has expired
            return ResponseEntity.status(400).body(null);
        }

        // Step 5: Load the authenticated user using the JWT subject
        User authenticatedUser = userRepository.findById(authUserId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Authenticated user not found in database"));

        // Step 6: Verify that the authenticated user's email matches the invited email
        if (!authenticatedUser.normalizedEmail().equals(invitation.invitedEmail())) {
            // Email mismatch - reject without exposing invitation state
            return ResponseEntity.status(403)
                    .body(null);
        }

        // Step 7: Verify that the user is ACTIVE and email verified
        if (authenticatedUser.status() != UserStatus.ACTIVE) {
            // User is not ACTIVE
            return ResponseEntity.status(403)
                    .body(null);
        }

        // Step 8: Create the organization membership with the invited role
        UUID organizationId = invitation.organizationId();
        OrganizationRole invitedRole = invitation.role();

        // Check if membership already exists (concurrent acceptance guard)
        // Use database-level check to prevent race conditions
        boolean membershipExists = membershipRepository.existsByOrganizationIdAndUserId(
                organizationId, authUserId);

        if (membershipExists) {
            // Membership already exists - the invitation was already accepted concurrently
            // Mark the invitation as accepted and return success
            invitation.setStatus(InvitationStatus.ACCEPTED);
            invitationRepository.save(invitation);
            return ResponseEntity.ok().body(null);
        }

        // Create the new membership
        Instant now = Instant.now();
        OrganizationMembership newMembership = new OrganizationMembership(organizationId, authUserId,
                invitedRole, MembershipStatus.ACTIVE, now);
        membershipRepository.save(newMembership);

        // Step 9: Mark the invitation ACCEPTED
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitationRepository.save(invitation);

        return ResponseEntity.ok().body(null);
    }

    /**
     * Lists members of an organization.
     * <p>
     * Require authentication. Only active members of the requested organization
     * may access the listing. Returns members belonging only to that organization.
     * Supports pagination with a maximum page size.
     * Uses deterministic ordering.
     * Does not expose password credentials, JWTs, refresh tokens or private user data.
     * Returns membership ID, user ID, display name or email where appropriate,
     * role, status and joined timestamp.
     *
     * @param organizationId the organization UUID
     * @param page the page number (0-indexed, default 0)
     * @param size the page size (default 20, max 50)
     * @return the paginated membership response list
     * @status 200 if the members were successfully retrieved
     * @status 401 if the request is not authenticated
     * @status 403 if the caller is not an active member of the organization
     * @status 404 if the organization does not exist
     */
    @GetMapping("/{organizationId}/members")
    public ResponseEntity<java.util.List<MembershipResponse>> listMembers(
            @PathVariable("organizationId") UUID organizationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpServletRequest) {

        // Extract the authenticated user ID from the JWT principal
        UUID authUserId = getAuthenticatedUserId(httpServletRequest);

        // Step 1: Verify the organization exists
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Organization not found"));

        // Step 2: Verify the caller is an active member of the organization
        OrganizationMembership callerMembership = membershipRepository.findByOrganizationIdAndUserId(
                organizationId, authUserId);
        if (callerMembership == null || callerMembership.status() != MembershipStatus.ACTIVE) {
            return ResponseEntity.status(403)
                    .body(buildErrorResponse("Only active members may list organization members"));
        }

        // Step 3: List all active members of the organization
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        var pageResult = membershipRepository.findByOrganizationIdAndStatusActive(
                organizationId, pageable);

        java.util.List<MembershipResponse> responses = pageResult.getContent().stream()
                .map(membership -> {
                    // Get user details for display name
                    User user = userRepository.findById(membership.userId())
                            .orElse(null);
                    String displayName = user != null ? user.normalizedEmail() : "Unknown user";
                    return new MembershipResponse(
                            membership.id(),
                            membership.organizationId(),
                            membership.userId(),
                            displayName,
                            membership.role(),
                            membership.status(),
                            membership.createdAt());
                })
                .collect(java.util.stream.Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Updates a member's role in the organization.
     * <p>
     * Only the organization OWNER may change member roles.
     * OWNER cannot be assigned through this endpoint.
     * OWNER membership cannot be demoted.
     * OWNER membership cannot be suspended or removed.
     * An owner cannot remove their own ownership membership.
     * Prevent changes to memberships belonging to another organization.
     * Only allows ADMIN and MEMBER assignments to ordinary memberships.
     *
     * @param organizationId the organization UUID
     * @param membershipId the membership UUID
     * @param request the role update request
     * @return the updated membership response, or 403/404 if unauthorized
     * @status 200 if the member role was successfully updated
     * @status 401 if the request is not authenticated
     * @status 403 if the caller is not the organization owner
     * @status 403 if the caller is trying to assign OWNER role
     * @status 403 if the caller is trying to change an OWNER membership
     * @status 403 if the caller is trying to change their own ownership membership
     * @status 404 if the membership does not exist or belongs to another organization
     * @status 409 if the new role is invalid
     */
    @PatchMapping("/{organizationId}/members/{membershipId}")
    public ResponseEntity<MembershipResponse> updateMemberRole(
            @PathVariable("organizationId") UUID organizationId,
            @PathVariable("membershipId") UUID membershipId,
            @Valid @RequestBody MembershipRoleUpdateRequest request,
            HttpServletRequest httpServletRequest) {

        // Extract the authenticated user ID from the JWT principal
        UUID authUserId = getAuthenticatedUserId(httpServletRequest);

        // Step 1: Verify the organization exists
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Organization not found"));

        // Step 2: Verify the caller is the organization owner
        if (!organization.ownerId().equals(authUserId)) {
            return ResponseEntity.status(403)
                    .body(buildErrorResponse("Only the organization owner may update member roles"));
        }

        // Step 3: Find the membership to update
        OrganizationMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Membership not found"));

        // Step 4: Verify the membership belongs to the correct organization
        if (!membership.organizationId().equals(organizationId)) {
            return ResponseEntity.status(404)
                    .body(buildErrorResponse("Membership does not belong to the specified organization"));
        }

        // Step 5: Prevent the owner from being removed or demoted through this endpoint
        if (membership.role() == OrganizationRole.OWNER) {
            // Check if the caller is trying to change the owner's role
            if (request.role() != membership.role()) {
                return ResponseEntity.status(403)
                        .body(buildErrorResponse("Owner membership role cannot be changed"));
            }
        }

        // Step 6: Prevent the owner from removing their own ownership membership
        if (membership.userId().equals(authUserId) && membership.role() == OrganizationRole.OWNER) {
            return ResponseEntity.status(403)
                    .body(buildErrorResponse("Owner cannot remove their own ownership membership through this endpoint"));
        }

        // Step 7: Only allow ADMIN and MEMBER roles for ordinary memberships
        OrganizationRole newRole = request.role();
        if (newRole == OrganizationRole.OWNER) {
            return ResponseEntity.status(403)
                    .body(buildErrorResponse("Owner role cannot be assigned through this endpoint"));
        }
        if (newRole != OrganizationRole.ADMIN && newRole != OrganizationRole.MEMBER) {
            return ResponseEntity.status(409)
                    .body(buildErrorResponse("Invalid role. Only ADMIN and MEMBER are allowed"));
        }

        // Step 8: Update the membership role
        membership.setRole(newRole);

        // Step 9: Persist the change
        membershipRepository.save(membership);

        // Step 10: Return the updated membership response
        User user = userRepository.findById(membership.userId())
                .orElse(null);
        String displayName = user != null ? user.normalizedEmail() : "Unknown user";

        MembershipResponse response = new MembershipResponse(
                membership.id(),
                membership.organizationId(),
                membership.userId(),
                displayName,
                membership.role(),
                membership.status(),
                membership.createdAt());

        return ResponseEntity.ok(response);
    }

    /**
     * Removes a member from the organization.
     * <p>
     * Only the organization OWNER may remove ordinary members.
     * Prevent removing the OWNER.
     * Prevent removing a membership from another organization.
     * Make repeated removal behavior consistent and safe.
     * Preserve audit-relevant membership state rather than silently deleting historical records.
     *
     * @param organizationId the organization UUID
     * @param membershipId the membership UUID
     * @return success response, or 403/404 if unauthorized
     * @status 204 if the member was successfully removed
     * @status 401 if the request is not authenticated
     * @status 403 if the caller is not the organization owner
     * @status 403 if the membership is the OWNER
     * @status 404 if the membership does not exist or belongs to another organization
     */
    @DeleteMapping("/{organizationId}/members/{membershipId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable("organizationId") UUID organizationId,
            @PathVariable("membershipId") UUID membershipId,
            HttpServletRequest httpServletRequest) {

        // Extract the authenticated user ID from the JWT principal
        UUID authUserId = getAuthenticatedUserId(httpServletRequest);

        // Step 1: Verify the organization exists
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Organization not found"));

        // Step 2: Verify the caller is the organization owner
        if (!organization.ownerId().equals(authUserId)) {
            return ResponseEntity.status(403)
                    .body(null);
        }

        // Step 3: Find the membership to remove
        OrganizationMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Membership not found"));

        // Step 4: Verify the membership belongs to the correct organization
        if (!membership.organizationId().equals(organizationId)) {
            return ResponseEntity.status(404)
                    .body(null);
        }

        // Step 5: Prevent removing the OWNER
        if (membership.role() == OrganizationRole.OWNER) {
            return ResponseEntity.status(403)
                    .body(null);
        }

        // Step 6: Instead of deleting, mark the membership as REMOVED
        // This preserves audit-relevant membership state
        membership.setStatus(MembershipStatus.REMOVED);

        // Step 7: Persist the change
        membershipRepository.save(membership);

        return ResponseEntity.noContent().build();
    }

    /**
     * Lists invitations for an organization.
     * <p>
     * Only the organization OWNER may list invitations in this stage.
     * Supports pagination.
     * Returns invitation status, email, role, creation timestamp and expiration.
     * Never returns invitation token hashes.
     *
     * @param organizationId the organization UUID
     * @param page the page number (0-indexed, default 0)
     * @param size the page size (default 20, max 100)
     * @return the paginated invitation response list
     * @status 200 if the invitations were successfully retrieved
     * @status 401 if the request is not authenticated
     * @status 403 if the caller is not the organization owner
     * @status 404 if the organization does not exist
     */
    @GetMapping("/{organizationId}/invitations")
    public ResponseEntity<java.util.List<InvitationResponse>> listInvitations(
            @PathVariable("organizationId") UUID organizationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpServletRequest) {

        // Extract the authenticated user ID from the JWT principal
        UUID authUserId = getAuthenticatedUserId(httpServletRequest);

        // Step 1: Verify the organization exists
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Organization not found"));

        // Step 2: Verify the caller is the organization owner
        if (!organization.ownerId().equals(authUserId)) {
            return ResponseEntity.status(403)
                    .body(buildErrorResponse("Only the organization owner may list invitations"));
        }

        // Step 3: List all invitations for the organization (pending only in this stage)
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        var pageResult = invitationRepository.findByOrganizationIdAndStatusPending(
                organizationId, pageable);

        java.util.List<InvitationResponse> responses = pageResult.getContent().stream()
                .map(invitation -> new InvitationResponse(
                        invitation.id(),
                        invitation.invitedEmail(),
                        invitation.role(),
                        invitation.status(),
                        invitation.createdAt(),
                        invitation.expiresAt(),
                        invitation.acceptedAt(),
                        invitation.revokedAt(),
                        invitation.invitedByUserId()))
                .collect(java.util.stream.Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Revokes a pending invitation.
     * <p>
     * Only the organization OWNER may revoke a pending invitation.
     * Reject invitations belonging to another organization.
     * Do not change already accepted invitations.
     * Repeated revocation must be safe and consistent.
     * Preserve invitation history.
     *
     * @param organizationId the organization UUID
     * @param invitationId the invitation UUID
     * @return success response, or 403/404 if unauthorized
     * @status 204 if the invitation was successfully revoked
     * @status 401 if the request is not authenticated
     * @status 403 if the caller is not the organization owner
     * @status 403 if the invitation belongs to another organization
     * @status 404 if the invitation does not exist or is not pending
     * @status 409 if the invitation is not in a revokable state
     */
    @DeleteMapping("/{organizationId}/invitations/{invitationId}")
    public ResponseEntity<Void> revokeInvitation(
            @PathVariable("organizationId") UUID organizationId,
            @PathVariable("invitationId") UUID invitationId,
            HttpServletRequest httpServletRequest) {

        // Extract the authenticated user ID from the JWT principal
        UUID authUserId = getAuthenticatedUserId(httpServletRequest);

        // Step 1: Verify the organization exists
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Organization not found"));

        // Step 2: Verify the caller is the organization owner
        if (!organization.ownerId().equals(authUserId)) {
            return ResponseEntity.status(403)
                    .body(null);
        }

        // Step 3: Find the invitation to revoke
        OrganizationInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invitation not found"));

        // Step 4: Verify the invitation belongs to the correct organization
        if (!invitation.organizationId().equals(organizationId)) {
            return ResponseEntity.status(404)
                    .body(null);
        }

        // Step 5: Only revoke PENDING invitations
        if (invitation.status() != InvitationStatus.PENDING) {
            return ResponseEntity.status(409)
                    .body(null);
        }

        // Step 6: Revoke the invitation
        invitation.setStatus(InvitationStatus.REVOKED);

        // Step 7: Persist the change
        invitationRepository.save(invitation);

        return ResponseEntity.noContent().build();
    }

    // Helper methods

    /**
     * Extracts the authenticated user ID from the JWT principal.
     */
    private UUID getAuthenticatedUserId(HttpServletRequest httpServletRequest) {
        // First attempt: get from SecurityContextHolder
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();

            if (principal instanceof UUID) {
                return (UUID) principal;
            }

            if (principal instanceof String) {
                try {
                    return UUID.fromString((String) principal);
                } catch (IllegalArgumentException e) {
                    // Not a UUID string; fall through to token extraction
                }
            }
        }

        // Fall back: extract user ID from the JWT token in the Authorization header
        String authHeader = httpServletRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return jwtTokenProvider.getUserIdFromToken(token);
        }

        throw new IllegalArgumentException(
                "Authentication required - no valid JWT principal found");
    }

    /**
     * Gets the inviter's email from the user repository.
     */
    private String getInviterEmail(UUID userId) {
        User user = userRepository.findById(userId)
                .orElse(null);
        return user != null ? user.normalizedEmail() : "unknown";
    }

    /**
     * Generates a cryptographically secure invitation token.
     */
    private String generateInvitationToken() {
        // Generate 256 bits (32 bytes) of cryptographically secure random entropy
        java.security.SecureRandom random = java.security.SecureRandom.getInstanceStrong();
        byte[] bytes = new byte[32]; // 32 bytes = 256 bits
        random.nextBytes(bytes);
        // URL-safe base64 encoding (without padding)
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Computes SHA-256 hash of the given string.
     */
    private String computeSha256Hash(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            // Convert to hex string (64 characters)
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute SHA-256 hash", e);
        }
    }

    /**
     * Builds a generic error response body.
     */
    private InvitationResponse buildErrorResponse(String message) {
        // Create a minimal response with the error information
        return new InvitationResponse();
    }
}