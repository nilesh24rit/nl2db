package com.nl2db.gateway.auth;

import com.nl2db.auth.*;
import com.nl2db.auth.VerificationTokenService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class VerificationController {

    private final VerificationTokenService verificationTokenService;
    private final UserRepository userRepository;

    public VerificationController(VerificationTokenService verificationTokenService,
                                    UserRepository userRepository) {
        this.verificationTokenService = verificationTokenService;
        this.userRepository = userRepository;
    }

    /**
     * Verify a user's email address using a verification token.
     * <p>
     * The workflow is:
     * 1. Validate the request containing the verification token
     * 2. Hash the supplied token and locate its record
     * 3. Verify that it is unexpired, unused and not revoked
     * 4. Load the associated user
     * 5. Verify the user's current status is PENDING_VERIFICATION
     * 6. Atomically consume the token
     * 7. Transition the user to ACTIVE using the existing domain transition rules
     * 8. Persist both changes transactionally
     * <p>
     * Security requirements:
     * - A token can be consumed only once
     * - Concurrent verification requests cannot both perform the transition
     * - Expired tokens are rejected
     * - Revoked tokens are rejected
     * - Unknown tokens are rejected
     * - Already consumed tokens are rejected
     * - Do not permit verification to reactivate SUSPENDED or DEACTIVATED users
     * - Do not bypass the User aggregate's status-transition rules
     * - Use row locking or conditional database updates where necessary
     *
     * @param request the verification request containing the raw token
     * @return the verification success response
     * @status 200 if verification succeeded
     * @status 400 if the token is invalid, expired, consumed, or revoked
     */
    @PostMapping("/verify-email")
    public ResponseEntity<VerificationResponse> verifyEmail(@Valid @RequestBody VerificationRequest request) {
        try {
            VerificationResult result = verificationTokenService.verifyToken(request.token());

            // Return success response without exposing internal persistence details
            return ResponseEntity.ok(new VerificationResponse(
                    result.user().id(),
                    result.user().normalizedEmail(),
                    result.user().status()
            ));
        } catch (VerificationException e) {
            // Return a consistent generic failure response for invalid, expired,
            // consumed, or revoked tokens
            return ResponseEntity.badRequest()
                    .body(new VerificationResponse(null, null, null));
        }
    }

    /**
     * Request a resend of the verification email.
     * <p>
     * The workflow is:
     * 1. Normalize the email using the existing EmailAddress abstraction
     * 2. If the user is PENDING_VERIFICATION, issue a new verification token
     * 3. Revoke or invalidate previous outstanding verification tokens
     * 4. Persist the new token hash before attempting delivery
     * 5. Do not issue verification tokens for ACTIVE users
     * 6. Do not reactivate suspended or deactivated accounts
     * 7. Apply a resend cooldown (configurable, initially 60 seconds)
     * 8. Prevent repeated requests from generating unlimited tokens
     * 9. Avoid revealing whether an email address is registered
     *
     * @param request the resend request containing the user's email address
     * @return the generic success response
     * @status 200 if the resend was initiated (or the user is already ACTIVE)
     * @status 400 if the request is invalid
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<ResendResponse> resendVerification(@Valid @RequestBody ResendRequest request) {
        // Normalize the email using the existing EmailAddress abstraction
        EmailAddress email;
        try {
            email = new EmailAddress(request.email());
        } catch (IllegalArgumentException e) {
            // Invalid email format - return generic response without revealing
            // whether the email is registered
            return ResponseEntity.badRequest()
                    .body(new ResendResponse(false));
        }

        String normalizedEmail = email.normalized();

        // Look up the user by normalized email
        var optionalUser = userRepository.findByNormalizedEmail(normalizedEmail);
        if (optionalUser.isEmpty()) {
            // User not found - return generic response without revealing
            // whether the email address is registered
            return ResponseEntity.ok(new ResendResponse(true));
        }

        User user = optionalUser.get();

        // Do not issue verification tokens for ACTIVE users
        if (user.status() == UserStatus.ACTIVE) {
            return ResponseEntity.ok(new ResendResponse(true));
        }

        // Do not reactivate suspended or deactivated accounts
        if (user.status() == UserStatus.SUSPENDED
                || user.status() == UserStatus.DEACTIVATED) {
            return ResponseEntity.ok(new ResendResponse(true));
        }

        // The user must be PENDING_VERIFICATION to get a new verification token
        // (other statuses like SUSPENDED/DEACTIVATED are already handled above)

        try {
            // Generate a new verification token (this will also revoke old ones)
            verificationTokenService.resendToken(user);
            return ResponseEntity.ok(new ResendResponse(true));
        } catch (Exception e) {
            // If token generation or email delivery fails, still return
            // a generic success response to avoid revealing information
            return ResponseEntity.ok(new ResendResponse(true));
        }
    }
}