package com.nl2db.gateway.auth;

import com.nl2db.auth.LoginResponse;
import com.nl2db.auth.LoginRequest;
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
 * Refresh endpoint for rotating refresh tokens.
 * <p>
 * Accepts a refresh token and returns a new access token and refresh token pair.
 * The presented refresh token is atomically consumed and replaced.
 * <p>
 * Endpoint: POST /api/v1/auth/refresh
 * Request body: { "refreshToken": "opaque-token-string" }
 * Successful response: { "accessToken": "...", "refreshToken": "...", "tokenType": "Bearer", "expiresIn": 900 }
 * <p>
 * Security requirements:
 * - Validate request size and token format
 * - Use database locking for atomic rotation
 * - Never return the same refresh token twice
 * - Revoke the entire family on reuse detection
 * - Return generic authentication failure (401) for all error cases
 * - Do not reveal whether a token exists or its family status
 */
@RestController
@RequestMapping("/api/v1/auth")
public class RefreshController {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public RefreshController(RefreshTokenRepository refreshTokenRepository,
                             UserRepository userRepository,
                             JwtTokenProvider jwtTokenProvider) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Refresh access and refresh tokens using a refresh token.
     * <p>
     * Workflow (atomic, transactional):
     * 1. Validate the request and extract the refresh token
     * 2. Hash the supplied refresh token
     * 3. Locate its persistence record by hash (with database lock)
     * 4. Verify it is unexpired and not revoked
     * 5. Verify the associated user still exists and is ACTIVE
     * 6. Verify the token has not already been consumed
     * 7. Atomically consume the presented token (database-level lock)
     * 8. Generate a new refresh token in the same family
     * 9. Persist the new token hash
     * 10. Generate a new JWT access token
     * 11. Return the new token pair
     * <p>
     * Concurrency control: Database-level pessimistic locking via
     * EntityManager lock() ensures that concurrent refresh requests
     * using the same token cannot both succeed. Only the first request
     * to acquire the lock will proceed; others will wait or fail.
     * <p>
     * Reuse detection: If a previously consumed token is presented,
     * the entire associated token family is revoked. This handles
     * replay attacks and rotation-then-replay scenarios.
     *
     * @param request the refresh request containing the refresh token
     * @return the new token pair response
     * @throws jakarta.ws.rs.NotAuthorizedException if the token is invalid,
     *   expired, revoked, consumed, or the user is inactive
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestBody RefreshRequest request) {
        String rawRefreshToken = request.refreshToken();

        // Step 1: Validate request - check token is not empty or too large
        if (rawRefreshToken == null || rawRefreshToken.trim().isEmpty()) {
            return ResponseEntity.status(UNAUTHORIZED)
                    .body(new LoginResponse(null, null, null, 0, null));
        }

        if (rawRefreshToken.length() > 1000) {
            return ResponseEntity.status(UNAUTHORIZED)
                    .body(new LoginResponse(null, null, null, 0, null));
        }

        // Step 2: Hash the supplied refresh token
        String tokenHash;
        try {
            tokenHash = sha256Hash(rawRefreshToken);
        } catch (Exception e) {
            return ResponseEntity.status(UNAUTHORIZED)
                    .body(new LoginResponse(null, null, null, 0, null));
        }

        // Step 3: Locate its persistence record by hash with database lock
        // Use pessimistic locking to prevent concurrent rotation success
        RefreshToken existingToken;
        try {
            existingToken = refreshTokenRepository.findByTokenHashLocked(tokenHash);
        } catch (Exception e) {
            return ResponseEntity.status(UNAUTHORIZED)
                    .body(new LoginResponse(null, null, null, 0, null));
        }

        // Step 4: Token not found - return generic failure (do not reveal existence)
        if (existingToken == null) {
            return ResponseEntity.status(UNAUTHORIZED)
                    .body(new LoginResponse(null, null, null, 0, null));
        }

        // Step 5: Verify not revoked
        if (existingToken.isRevoked()) {
            return ResponseEntity.status(UNAUTHORIZED)
                    .body(new LoginResponse(null, null, null, 0, null));
        }

        // Step 6: Verify not expired
        if (existingToken.isExpired()) {
            return ResponseEntity.status(UNAUTHORIZED)
                    .body(new LoginResponse(null, null, null, 0, null));
        }

        // Step 7: Verify the associated user still exists and is ACTIVE
        User user = existingToken.user();
        if (user == null || user.status() != com.nl2db.auth.UserStatus.ACTIVE) {
            return ResponseEntity.status(UNAUTHORIZED)
                    .body(new LoginResponse(null, null, null, 0, null));
        }

        // Step 8: Verify the token has not already been consumed
        if (existingToken.isConsumed()) {
            // Reuse detection: revoke the entire token family
            // This handles replay attacks and rotation-then-replay scenarios
            refreshTokenRepository.revokeFamily(existingToken.familyId(), "reuse_detection");
            return ResponseEntity.status(UNAUTHORIZED)
                    .body(new LoginResponse(null, null, null, 0, null));
        }

        // Step 9: Atomically consume the presented token
        // Use database pessimistic lock to ensure exclusive access
        RefreshToken lockedToken = refreshTokenRepository.lockTokenForUpdate(existingToken.id());

        // Double-check consumption status after acquiring lock
        if (lockedToken.isConsumed()) {
            // Another request consumed it first; still revoke family for safety
            refreshTokenRepository.revokeFamily(lockedToken.familyId(), "reuse_detection");
            return ResponseEntity.status(UNAUTHORIZED)
                    .body(new LoginResponse(null, null, null, 0, null));
        }

        // Mark the token as consumed and link to replacement
        lockedToken.setConsumedAt(Instant.now());
        // We'll set the replacement token ID after creating the new token
        lockedToken.setReplacementTokenId(null);
        refreshTokenRepository.update(lockedToken);

        // Step 10: Generate a new refresh token in the same family
        UUID familyId = lockedToken.familyId();
        String newRawRefreshToken = generateRawRefreshToken();
        String newTokenHash = sha256Hash(newRawRefreshToken);

        Instant now = Instant.now();
        long refreshTokenTtlSeconds = 30 * 24 * 60 * 60; // 30 days
        Instant newRefreshTokenExpiresAt = now.plusSeconds(refreshTokenTtlSeconds);

        RefreshToken newRefreshToken = new RefreshToken(
                UUID.randomUUID(),
                user.id(),
                newTokenHash,
                familyId,
                now,
                newRefreshTokenExpiresAt
        );

        // Set replacement linkage: new token replaces the old one
        newRefreshToken.setReplacementTokenId(lockedToken.id());
        lockedToken.setReplacementTokenId(newRefreshToken.id());

        // Persist the new refresh token hash
        newRefreshToken.setUser(user);
        refreshTokenRepository.save(newRefreshToken);
        refreshTokenRepository.update(lockedToken);

        // Step 11: Generate a new JWT access token
        String newAccessToken = jwtTokenProvider.createAccessToken(user.id());

        // Step 12: Return the new token pair
        long expiresInSeconds = JwtTokenProvider.JWT_TTL_SECONDS;
        Instant newExpiresAt = Instant.now().plusSeconds(expiresInSeconds);

        return ResponseEntity.ok(
                new LoginResponse(newAccessToken, "Bearer", newRawRefreshToken, expiresInSeconds, newExpiresAt));
    }

    /**
     * Refresh request DTO.
     */
    public static class RefreshRequest {

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