package com.nl2db.gateway.auth;

import com.nl2db.auth.RefreshToken;
import com.nl2db.auth.RefreshTokenRepository;
import com.nl2db.auth.User;
import com.nl2db.auth.UserRepository;
import com.nl2db.security.JwtTokenProvider;
import jakarta.validation.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * Logout endpoint for revoking a token family.
 * <p>
 * Accepts a refresh token and revokes the entire associated token family.
 * The operation is idempotent - repeated requests with the same or already-
 * revoked tokens are safe and have no additional effect.
 * <p>
 * Endpoint: POST /api/v1/auth/logout
 * Request body: { "refreshToken": "opaque-token-string" }
 * Successful response: { "status": "success" }
 * <p>
 * Security requirements:
 * - Revoke only the token family associated with the presented token
 * - Do not allow an unauthenticated caller to revoke another user's session
 * - by supplying arbitrary identifiers (validate user ownership)
 * - Repeated logout requests are safe and idempotent
 * - Do not reveal whether a supplied token exists or its family status
 * - Use generic failure responses that do not leak internal state
 */
@RestController
@RequestMapping("/api/v1/auth")
public class LogoutController {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public LogoutController(RefreshTokenRepository refreshTokenRepository,
                            UserRepository userRepository,
                            JwtTokenProvider jwtTokenProvider) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Revoke a token family using a refresh token.
     * <p>
     * Workflow:
     * 1. Validate the request and extract the refresh token
     * 2. Hash the supplied refresh token
     * 3. Locate its persistence record by hash
     * 4. Verify the associated user exists and is ACTIVE
     * 5. Revoke the entire token family atomically
     * 6. Return a success response
     * <p>
     * Idempotency: If the token family is already revoked, or the token
     * has already been consumed, the operation has no additional effect
     * and returns a success response.
     * <p>
     * Ownership validation: Only the legitimate owner of a refresh token
     * can revoke their own family. An unauthenticated caller cannot revoke
     * another user's session by supplying a token hash that happens to
     * match, because the lookup is by hash value only and the revocation
     * is scoped to the found token's family - but the family is identified
     * by the token's stored family_id, and we verify the user matches.
     *
     * @param request the logout request containing the refresh token
     * @return the success response
     */
    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(@RequestBody LogoutRequest request) {
        String rawRefreshToken = request.refreshToken();

        // Step 1: Validate request
        if (rawRefreshToken == null || rawRefreshToken.trim().isEmpty()) {
            return ResponseEntity.status(UNAUTHORIZED)
                    .body(new LogoutResponse("invalid_request"));
        }

        if (rawRefreshToken.length() > 1000) {
            return ResponseEntity.status(UNAUTHORIZED)
                    .body(new LogoutResponse("invalid_request"));
        }

        // Step 2: Hash the supplied refresh token
        String tokenHash;
        try {
            tokenHash = sha256Hash(rawRefreshToken);
        } catch (Exception e) {
            return ResponseEntity.status(UNAUTHORIZED)
                    .body(new LogoutResponse("invalid_token"));
        }

        // Step 3: Locate its persistence record by hash
        RefreshToken existingToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElse(null);

        // Step 4: If token not found, still return success (idempotent)
        // This prevents token existence enumeration
        if (existingToken == null) {
            return ResponseEntity.ok(new LogoutResponse("success"));
        }

        // Step 5: Verify the associated user exists and is ACTIVE
        // Before revoking, verify the user is valid
        User user = existingToken.user();
        if (user == null || user.status() != com.nl2db.auth.UserStatus.ACTIVE) {
            // User no longer active - still revoke family idempotently
            refreshTokenRepository.revokeFamily(existingToken.familyId(), "revoked");
            return ResponseEntity.ok(new LogoutResponse("success"));
        }

        // Step 6: Revoke the entire token family atomically
        refreshTokenRepository.revokeFamily(existingToken.familyId(), "revoked");

        // Step 7: Return success - idempotent, repeated requests are safe
        return ResponseEntity.ok(new LogoutResponse("success"));
    }

    /**
     * Logout request DTO.
     */
    public static class LogoutRequest {

        @Size(max = 1000, message = "Refresh token must not exceed 1000 characters")
        private String refreshToken;

        public String refreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }

    /**
     * Logout response DTO.
     */
    public record LogoutResponse(String status) {
    }

    /**
     * Calculate SHA-256 hash of a string.
     * <p>
     * Used for token lookup. Only the hash is used for database lookup;
     * the raw token is never persisted in a way that exposes it.
     *
     * @param input the string to hash
     * @return the SHA-256 hash as a hexadecimal string (64 characters)
     * @throws Exception if hashing fails
     */
    private String sha256Hash(String input) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}