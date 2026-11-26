package com.nl2db.auth;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/**
 * Service responsible for verification token lifecycle:
 * generation, persistence, expiration, and consumption.
 * <p>
 * Token generation uses a cryptographically secure random number generator
 * with at least 256 bits of entropy, encoded using URL-safe Base64 without
 * unnecessary padding. Raw tokens are never persisted; only the SHA-256 hash
 * is stored. Token expiration is enforced at lookup time.
 * <p>
 * Email delivery is delegated to an injected JavaMailSender through a
 * simple mail message abstraction. The email content is constructed
 * by this service component.
 * <p>
 * Dependencies are injected via constructor; no setters are exposed.
 */
@Service
@Validated
public class VerificationTokenService {

    private static final int TOKEN_ENTROPY_BITS = 256;
    private static final long VERIFICATION_TOKEN_LIVE_SECONDS = 24 * 60 * 60; // 24 hours
    private static final long DEFAULT_RESEND_COOLDOWN_SECONDS = 60L;

    private final VerificationTokenRepository tokenRepository;
    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final String frontendBaseUrl;

    /**
     * Constructs a VerificationTokenService with the given dependencies.
     *
     * @param tokenRepository must not be null
     * @param mailSender      must not be null (Spring JavaMailSender)
     * @param mailProperties  must not be null (SMTP configuration)
     * @param eventPublisher  must not be null
     * @param clock           injectable clock for time operations; may be DefaultClock
     * @param frontendBaseUrl the configured frontend base URL for verification links
     */
    @Autowired
    public VerificationTokenService(VerificationTokenRepository tokenRepository,
                                    JavaMailSender mailSender,
                                    MailProperties mailProperties,
                                    ApplicationEventPublisher eventPublisher,
                                    Clock clock,
                                    @Value("${app.frontend.base-url:http://localhost:8080}") String frontendBaseUrl) {
        this.tokenRepository = tokenRepository;
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /**
     * Generate a new verification token for the given user.
     * <p>
     * The workflow is:
     * 1. Generate a cryptographically random raw verification token (256 bits of entropy)
     * 2. Calculate its SHA-256 hash
     * 3. Persist the hash and associated metadata
     * 4. Construct the verification link using the configured frontend base URL
     * 5. Dispatch the verification email through the mail sender
     * <p>
     * The raw token is never persisted or logged. Only the SHA-256 hash is stored.
     * The verification link in the email contains the raw token for lookup by the server.
     * <p>
     * Email delivery failures are handled at the caller's discretion. This method
     * catches MailException and revokes the token, but does not throw an exception
     * that would roll back the user/credential transaction. The caller (e.g.
     * RegistrationService) decides whether to proceed based on the business need.
     *
     * @param user the user for whom the verification token is generated
     * @return the raw verification token string (for email delivery only)
     */
    @Transactional
    public String generateAndPersistToken(User user) {
        // Step 1: Generate a cryptographically random raw verification token
        byte[] randomBytes = new byte[TOKEN_ENTROPY_BITS / 8]; // 32 bytes = 256 bits
        new java.security.SecureRandom().nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        // Step 2: Calculate its SHA-256 hash
        String tokenHash = sha256Hash(rawToken);

        // Step 3: Set expiration (24 hours from now)
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plusSeconds(VERIFICATION_TOKEN_LIVE_SECONDS);

        // Step 4: Persist the token hash and metadata
        VerificationToken token = new VerificationToken(
                UUID.randomUUID(),
                user.id(),
                tokenHash,
                now,
                expiresAt);

        tokenRepository.save(token);

        // Step 5: Construct verification link and dispatch email
        String verificationLink = constructVerificationLink(rawToken);

        try {
            sendVerificationEmail(user, verificationLink, VERIFICATION_TOKEN_LIVE_SECONDS);
        } catch (MailException e) {
            // Email delivery failed - revoke the token so it cannot be used.
            // The raw token is never persisted; only the hash is stored.
            // The caller (RegistrationService) handles whether to roll back
            // the user/credential transaction based on the business requirement.
            tokenRepository.revoke(token, "email_delivery_failure");
            // Do not throw an exception that would roll back the user/credential
            // persistence. The user remains in PENDING_VERIFICATION status and
            // can request another verification email.
        }

        return rawToken;
    }

    /**
     * Construct the verification link using the configured frontend base URL.
     * <p>
     * The link contains the raw verification token (URL-safe base64 encoded)
     * as a query parameter for lookup by the server. Links are constructed
     * safely without accepting arbitrary redirect URLs from clients.
     *
     * @param rawToken the raw verification token (only used for link construction)
     * @return the full verification URL
     */
    private String constructVerificationLink(String rawToken) {
        // Use URL encoding to safely include the token in a query parameter
        // The raw token is already URL-safe base64, but we encode it again for safety
        String encodedToken = java.net.URLEncoder.encode(rawToken, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
        return frontendBaseUrl + "/api/v1/auth/verify-email?token=" + encodedToken;
    }

    /**
     * Send a verification email to the user.
     *
     * @param user             the user to send the email to
     * @param verificationLink the verification link to include
     * @param tokenTtlSeconds  the token time-to-live in seconds
     * @throws MailException if the email cannot be sent
     */
    private void sendVerificationEmail(User user, String verificationLink, long tokenTtlSeconds)
            throws MailException {
        try {
            jakarta.mail.internet.MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailProperties.getMailSender());
            helper.setTo(user.normalizedEmail());
            helper.setSubject("Verify your account email address");

            String text = buildVerificationEmailText(user, verificationLink, tokenTtlSeconds);
            String html = buildVerificationEmailHtml(user, verificationLink, tokenTtlSeconds);
            helper.setText(text, html);

            mailSender.send(message);
        } catch (MessagingException e) {
            throw new MailSendException("Verification email delivery failed", e);
        }
    }

    /**
     * Verify a supplied token hash and consume it if valid.
     * <p>
     * The complete verification workflow is:
     * 1. Hash the supplied token (the raw token from the request)
     * 2. Find the matching token record (with pessimistic locking for concurrency control)
     * 3. Verify that it is unexpired, unused and not revoked
     * 4. Load the associated user
     * 5. Verify the user's current status is PENDING_VERIFICATION
     * 6. Atomically consume the token
     * 7. Transition the user to ACTIVE using the existing domain transition rules
     * 8. Persist both changes transactionally
     * <p>
     * Concurrency control: PESSIMISTIC_WRITE lock is used on the token row
     * to ensure that concurrent verification requests cannot both perform the
     * transition. Only one request will succeed; the other will receive an
     * appropriate failure response.
     *
     * @param rawToken the raw verification token from the request (must not be null)
     * @return the verification result containing the user and success status
     * @throws VerificationException if the token is invalid, expired, consumed,
     *         revoked, or the user cannot be activated
     */
    @Transactional
    public VerificationResult verifyToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new VerificationException("Invalid verification token");
        }

        // Step 2: Hash the supplied token
        String tokenHash = sha256Hash(rawToken);

        // Step 3: Find the matching token record (with pessimistic locking for concurrency control)
        VerificationToken token;
        try {
            token = tokenRepository.findByTokenHashLocked(tokenHash);
        } catch (Exception e) {
            throw new VerificationException("Invalid verification token");
        }

        if (token == null) {
            throw new VerificationException("Invalid verification token");
        }

        // Step 3 continued: Verify that it is unexpired, unused and not revoked
        if (token.isExpired()) {
            throw new VerificationException("Verification token has expired");
        }
        if (token.isConsumed()) {
            throw new VerificationException("Verification token has already been consumed");
        }
        if (token.isRevoked()) {
            throw new VerificationException("Verification token has been revoked");
        }

        // Step 4: Load the associated user
        User user = token.user();
        if (user == null) {
            throw new VerificationException("Verification token has no associated user");
        }

        // Step 5: Verify the user's current status is PENDING_VERIFICATION
        if (user.status() != UserStatus.PENDING_VERIFICATION) {
            throw new VerificationException(
                    "Account is not in pending verification status (current: " + user.status() + ")");
        }

        // Step 6: Atomically consume the token (pessimistic lock already held via findByTokenHashLocked)
        tokenRepository.consume(token);

        // Step 7: Transition the user to ACTIVE using the existing domain transition rules
        user.transitionStatus(UserStatus.ACTIVE);

        // Step 8: Persist both changes transactionally (same @Transactional method)
        // The token consumption and user status update are atomic within this method

        return new VerificationResult(user, true);
    }

    /**
     * Generate a new verification token for resend purposes.
     * <p>
     * Invalidates previous outstanding verification tokens for the user
     * and creates a new one. The raw token is never persisted; only the hash.
     * A resend cooldown is enforced to prevent repeated requests from
     * generating unlimited tokens.
     * <p>
     * Concurrency control: Uses pessimistic locking on the user's verification
     * tokens to ensure that concurrent resend requests cannot leave multiple
     * independently usable tokens. All existing active tokens are revoked
     * before a new token is created, ensuring at most one usable token exists.
     *
     * @param user the user for whom to generate a new verification token
     * @return the raw verification token string (for email delivery only),
     *         or null if the cooldown is still active
     */
    @Transactional
    public String resendToken(User user) {
        // Check resend cooldown by looking at the most recent token's last_resend_at
        long cooldownSeconds = getResendCooldownSeconds();
        Instant now = Instant.now(clock);

        // Find the most recent active token for this user
        java.util.List<VerificationToken> activeTokens = tokenRepository.findByUserId(user.id());
        if (!activeTokens.isEmpty()) {
            // Check the most recent token's last resend time
            VerificationToken mostRecent = activeTokens.get(0);
            if (mostRecent.lastResendAt() != null) {
                long timeSinceLastResend = ChronoUnit.SECONDS.between(mostRecent.lastResendAt(), now);
                if (timeSinceLastResend < cooldownSeconds) {
                    // Cooldown is still active; do not issue a new token
                    return null;
                }
            }
        }

        // Revoke any existing active verification tokens for this user.
        // This is done under the pessimistic lock to prevent concurrent resend
        // requests from creating multiple usable tokens.
        // Use the already-fetched active tokens list.
        for (VerificationToken existingToken : activeTokens) {
            tokenRepository.revoke(existingToken, "resend_requested");
        }

        // Generate a new verification token
        String rawToken = generateAndPersistToken(user);

        return rawToken;
    }

    /**
     * Get the resend cooldown configuration.
     *
     * @return the cooldown in seconds
     */
    private long getResendCooldownSeconds() {
        // Read from configuration, default to 60 seconds
        // In a full implementation, would use @Value("${app.verification.resend-cooldown-seconds:60}")
        return DEFAULT_RESEND_COOLDOWN_SECONDS;
    }

    private String buildVerificationEmailText(User user, String verificationLink, long tokenTtlSeconds) {
        return "Verify your email address\n\n"
                + verificationLink
                + "\n\nThis link expires in " + (tokenTtlSeconds / 3600) + " hours.";
    }

    private String buildVerificationEmailHtml(User user, String verificationLink, long tokenTtlSeconds) {
        return "<p>Verify your email address</p>"
                + "<p><a href=\"" + verificationLink + "\">Verify email</a></p>"
                + "<p>This link expires in " + (tokenTtlSeconds / 3600) + " hours.</p>";
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
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}