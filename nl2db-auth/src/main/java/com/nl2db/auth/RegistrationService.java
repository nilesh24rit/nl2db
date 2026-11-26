package com.nl2db.auth;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import com.nl2db.security.PasswordHashingService;

import java.time.Instant;
import java.util.UUID;

/**
 * Application-level registration workflow.
 * <p>
 * Responsibilities:
 * - Validate the registration request (email presence, format, normalization, password)
 * - Check for duplicate email (application-level pre-check + database constraint)
 * - Create a new User aggregate with PENDING_VERIFICATION status
 * - Encode the raw password using the configured Argon2id hasher
 * - Persist the user and its credential atomically
 * - Return a response that does not expose internal persistence details
 * <p>
 * Transaction boundaries ensure that account creation is consistent:
 * if persistence fails, the entire operation is rolled back and no
 * partially created account is left in the database.
 */
@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final CredentialRepository credentialRepository;
    private final PasswordHashingService passwordHashingService;

    /**
     * Constructs a RegistrationService with the given repositories.
     *
     * @param userRepository       must not be null
     * @param credentialRepository must not be null
     * @param passwordHashingService must not be null
     */
    public RegistrationService(UserRepository userRepository,
                                 CredentialRepository credentialRepository,
                                 PasswordHashingService passwordHashingService) {
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.passwordHashingService = passwordHashingService;
    }

    /**
     * Registers a new user with the given email and password.
     * <p>
     * The workflow is:
     * 1. Normalize the email using the EmailAddress domain abstraction
     * 2. Check whether an account already exists for that normalized email
     * 3. Encode the raw password using Argon2id
     * 4. Create a new User aggregate with PENDING_VERIFICATION status
     * 5. Create a Credential entity with the encoded hash
     * 6. Persist both user and credential through their repositories
     * 7. Return a registration response with public-facing user identity
     * <p>
     * The method is transactional so that account creation is atomic:
     * if persistence fails, no partially created account is left in the database.
     *
     * @param request the registration request containing the email and password
     * @return the registration response with user identity information
     * @throws DuplicateEmailException if a user with the same normalized email already exists
     * @throws IllegalArgumentException if the email or password is invalid
     */
    @Transactional
    public RegistrationResponse register(RegistrationRequest request) {
        // Normalize the email using the existing EmailAddress domain abstraction
        EmailAddress email = new EmailAddress(request.email());
        String normalizedEmail = email.normalized();

        // Check whether an account already exists for that normalized email
        if (userRepository.existsByNormalizedEmail(normalizedEmail)) {
            throw new DuplicateEmailException(normalizedEmail);
        }

        // Encode the raw password using Argon2id
        String encodedHash = passwordHashingService.encode(request.password());

        // Create a new User aggregate with PENDING_VERIFICATION status
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        User user = new User(userId, email, UserStatus.PENDING_VERIFICATION, now);

        // Create a Credential entity with the encoded hash
        Credential credential = new Credential(user, encodedHash);

        // Persist both user and credential atomically
        userRepository.save(user);
        credentialRepository.save(credential);

        // Return a response that does not expose internal persistence details
        return RegistrationResponse.fromUser(user);
    }
}