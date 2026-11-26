package com.nl2db.gateway.auth;

import com.nl2db.auth.DuplicateEmailException;
import com.nl2db.auth.RegistrationRequest;
import com.nl2db.auth.RegistrationResponse;
import com.nl2db.auth.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    /**
     * Registers a new user account.
     * <p>
     * The endpoint is publicly accessible for initial account creation.
     * Validation is performed on the request payload.
     * Duplicate email attempts are rejected with a consistent conflict response.
     * Internal exceptions are never leaked to the client.
     *
     * @param request the registration request containing the email address
     * @return the registration response with user identity information
     * @status 201 if the user was successfully created
     * @status 400 if the request validation fails (missing/blank/malformed email)
     * @status 409 if the email address is already registered
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse register(@Valid @RequestBody RegistrationRequest request) {
        return registrationService.register(request);
    }
}