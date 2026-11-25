package com.nl2db.auth.dto;

import jakarta.validation.*;
import java.time.Instant;

/**
 * Request DTO for API key generation.
 * <p>
 * Contains the human-readable key name provided by the authenticated user.
 * The organization is derived from the trusted tenant context, not from
 * the request body. The key name is validated to be non-null and non-empty.
 */
public class ApiKeyGenerateRequest {

    @NotBlank(message = "API key name must not be blank")
    @Size(min = 1, max = 255, message = "API key name must be between 1 and 255 characters")
    private String keyName;

    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }
}