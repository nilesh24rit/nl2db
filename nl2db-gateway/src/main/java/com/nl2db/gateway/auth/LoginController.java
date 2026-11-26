package com.nl2db.gateway.auth;

import com.nl2db.auth.LoginRequest;
import com.nl2db.auth.LoginResponse;
import com.nl2db.auth.LoginService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class LoginController {

    private final LoginService loginService;

    public LoginController(LoginService loginService) {
        this.loginService = loginService;
    }

    /**
     * Authenticates a user and returns a JWT access token.
     * <p>
     * The endpoint is publicly accessible for user authentication.
     * Validation is performed on the request payload.
     * Invalid authentication attempts receive a consistent 401 response
     that does not reveal whether an email address exists or what the
     account status is.
     * Internal exceptions are never leaked to the client.
     *
     * @param request the login request containing the email and password
     * @return the login response with access token and expiration metadata
     * @status 200 if the user was successfully authenticated
     * @status 401 if the authentication credentials are invalid (unknown
     *          email, incorrect password, missing credential, or ineligible
 *          account status)
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = loginService.login(request);
        return ResponseEntity.ok(response);
    }
}