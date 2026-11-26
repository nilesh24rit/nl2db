package com.nl2db.auth;

import com.nl2db.auth.RefreshTokenRepository;

import com.nl2db.security.PasswordHashingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nl2db.security.JwtTokenProvider;

import java.time.Instant;
import java.util.UUID;

/**
 * Application-level login workflow.
 * <p>
 * Responsibilities:
 * - Validate the login request (email presence, format, password)
 * - Normalize the email using the EmailAddress domain abstraction
 * - Find the corresponding user by normalized email
 * - Retrieve the user's credential through the internal credential repository
 * - Verify the supplied password using the existing Argon2id abstraction
 * - Verify that the account is eligible to authenticate (ACTIVE only)
 * - Generate a signed JWT access token via JwtTokenProvider
 * - Generate and persist a new refresh token in a new token family
 * - Return the token and appropriate expiration metadata
 * <p>
 * Transactional boundaries ensure that the authentication workflow is
 * consistent: if token generation fails, no partial authentication state
 * is left in the database.
 * <p>
 * Security requirements:
 * - Never store or log the raw password
 * - Never expose the encoded password hash
 * - Never accept a user ID or account status from the login request
 * - Only ACTIVE users may authenticate
 * - PENDING_VERIFICATION, SUSPENDED and DEACTIVATED users must not
 *   receive access tokens
 * - Use a generic authentication failure response that does not reveal
 *   whether an email address exists or what the account status is
 * - Refresh tokens are generated with cryptographic randomness
 * - Only the SHA-256 hash of each refresh token is persisted
 * - Raw refresh token is returned only once in the login response
 * - Never persist or log the raw refresh token
 */
@Service
public class LoginService {

    private final UserRepository userRepository;
    private final CredentialRepository credentialRepository;
    private final PasswordHashingService passwordHashingService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Constructs a LoginService with the given dependencies.
     *
     * @param userRepository       must not be null
     * @param credentialRepository must not be null
     * @param passwordHashingService must not be null
     * @param jwtTokenProvider     must not be null
     */
    public LoginService(UserRepository userRepository,
                        CredentialRepository credentialRepository,
                        PasswordHashingService passwordHashingService,
                        JwtTokenProvider jwtTokenProvider,
                        RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.passwordHashingService = passwordHashingService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * Authenticates the user with the given login request.
     * <p>
     * The workflow is:
     * 1. Normalize the email using the existing EmailAddress abstraction
     * 2. Find the corresponding user by normalized email
     * 3. Retrieve the user's credential through the credential repository
     * 4. Verify the supplied password using Argon2id
     * 5. Verify that the account is eligible to authenticate (ACTIVE only)
     * 6. Generate a signed JWT access token
     * 7. Generate a new refresh token in a new token family
     * 8. Persist the refresh token hash
     * 9. Return the token pair and expiration metadata
     * <p>
     * Authentication failure behavior:
     * - Returns a consistent generic failure for unknown email, incorrect
     *   password, missing credential, or ineligible account status
     * - Never reveals whether an email address exists
     * - Never reveals internal account status in failure responses
     * - Never exposes password-hashing or persistence implementation details
     * - If authentication fails, no refresh token records are created
     *
     * @param request the login request containing the email and password
     * @return the login response with access token and refresh token
     * @throws AuthenticationException if authentication fails for any reason
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        // Normalize the email using the existing EmailAddress abstraction
        EmailAddress email = new EmailAddress(request.email());
        String normalizedEmail = email.normalized();

        // Find the corresponding user by normalized email
        User user = userRepository.findByNormalizedEmail(normalizedEmail);
        if (user == null) {
            throw new AuthenticationException("Invalid authentication credentials");
        }

        // Retrieve the user's credential through the credential repository
        // (using internal method that joins with user_id foreign key)
        var credentialOptional = credentialRepository.findByUserId(user.id());

        if (credentialOptional.isEmpty()) {
            throw new AuthenticationException(
                    "Invalid authentication credentials");
        }

        String encodedHash = credentialOptional.get().hash();

        // Verify the supplied password using the existing Argon2id abstraction
        boolean passwordMatches = passwordHashingService.verify(request.password(), encodedHash);

        if (!passwordMatches) {
            throw new AuthenticationException(
                    "Invalid authentication credentials");
        }

        // Verify that the account is eligible to authenticate (ACTIVE only)
        UserStatus status = user.status();
        if (status != UserStatus.ACTIVE) {
            throw new AuthenticationException(
                    "Invalid authentication credentials");
        }

        // Generate a signed JWT access token
        String accessToken = jwtTokenProvider.createAccessToken(user.id());

        // Generate a new refresh token in a new token family
        UUID familyId = UUID.randomUUID();
        String rawRefreshToken = generateRawRefreshToken();
        String tokenHash = sha256Hash(rawRefreshToken);

        Instant now = Instant.now();
        long refreshTokenTtlSeconds = 30 * 24 * 60 * 60; // 30 days
        Instant refreshTokenExpiresAt = now.plusSeconds(refreshTokenTtlSeconds);

        RefreshToken refreshToken = new RefreshToken(
                UUID.randomUUID(),
                user.id(),
                tokenHash,
                familyId,
                now,
                refreshTokenExpiresAt
        );

        // Persist the refresh token
        refreshToken.setUser(user);
        refreshToken.setConsumedAt(null);
        refreshToken.setRevokedAt(null);
        refreshToken.setReplacementTokenId(null);
        refreshToken.setRevocationReason(null);
        refreshTokenRepository.save(refreshToken);

        // Return the token pair and expiration metadata
        long expiresInSeconds = JwtTokenProvider.JWT_TTL_SECONDS;
        Instant expiresAt = Instant.now().plusSeconds(expiresInSeconds);

        return new LoginResponse(accessToken, "Bearer", rawRefreshToken, expiresInSeconds, expiresAt);
    }

    /**
     * Generate a cryptographically random refresh token value.
     * <p>
     * Uses a secure random number generator with at least 256 bits of entropy.
     * The token is encoded using URL-safe base64 representation.
     *
     * @return the raw refresh token string
     */
    private String generateRawRefreshToken() {
        byte[] randomBytes = new byte[32]; // 256 bits of entropy
        new java.security.SecureRandom().nextBytes(randomBytes);
        return java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }

    /**
     * Calculate SHA-256 hash of a string.
     * <p>
     * Used for token persistence and lookup. Only the hash is stored;
     * the raw token is never persisted.
     *
     * @param input the string to hash
     * @return the SHA-256 hash as a hexadecimal string (64 characters)
     */
    private String sha256Hash(String input) {
        try {
            java.security.MessageDigest digest =
                    java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
}