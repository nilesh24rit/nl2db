package com.nl2db.gateway.auth;

import com.nl2db.auth.ApiKeyService;
import com.nl2db.common.ApiKeyUtils;
import com.nl2db.security.ApiKeyPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/api-keys")
public class ApiKeyController {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping("/validate")
    public ResponseEntity<ApiKeyValidationResponse> validateKey(
            @Valid @NotBlank(message = "API key must not be blank")
            @RequestHeader(value = "X-API-Key", required = true) String apiKey) {

        if (apiKey == null || apiKey.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(new ApiKeyValidationResponse(
                            "API key is missing",
                            null,
                            null,
                            "missing"));
        }

        if (!ApiKeyUtils.isValidFormat(apiKey)) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(new ApiKeyValidationResponse(
                            "API key is malformed",
                            null,
                            null,
                            "malformed"));
        }

        try {
            var result = apiKeyService.validateApiKey(apiKey);

            if (!result.isValid()) {
                log.debug("API key validation failed: {}", result.getReason());
                return ResponseEntity
                        .status(HttpStatus.FORBIDDEN)
                        .body(new ApiKeyValidationResponse(
                                result.getReason(),
                                null,
                                null,
                                result.getErrorType()));
            }

            ApiKeyPrincipal principal = result.getPrincipal();
            log.debug("API key validated successfully: id={}, org={}",
                    principal.getApiKeyId(), principal.getOrganizationId());

            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(new ApiKeyValidationResponse(
                            "API key is valid",
                            principal.getApiKeyId(),
                            principal.getOrganizationId(),
                            "valid"));

        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(new ApiKeyValidationResponse(
                            "API key format is invalid",
                            null,
                            null,
                            "invalid_format"));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<ApiKeyMeResponse> me() {
        ApiKeyPrincipal principal = (ApiKeyPrincipal) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        return ResponseEntity.ok(new ApiKeyMeResponse(
                principal.getApiKeyId(),
                principal.getOrganizationId(),
                principal.getAuthType(),
                principal.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList()));
    }
}