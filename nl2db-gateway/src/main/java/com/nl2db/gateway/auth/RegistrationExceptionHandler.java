package com.nl2db.gateway.auth;

import com.nl2db.auth.DuplicateEmailException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Global exception handler for registration-related exceptions.
 * <p>
 * Ensures consistent error response formats and prevents leaking of
 * internal exception details or SQL error messages to clients.
 */
@RestControllerAdvice(basePackages = "com.nl2db.gateway.auth")
public class RegistrationExceptionHandler {

    /**
     * Handles duplicate email attempts.
     * <p>
     * Translates {@link DuplicateEmailException} into an HTTP 409
     * Conflict response with a consistent error message that does not
     * expose whether the existing account is active, suspended, or in
     * any other state.
     *
     * @param ex the duplicate email exception
     * @return a response entity with HTTP 409 status
     */
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<Object> handleDuplicateEmail(DuplicateEmailException ex) {
        String errorMessage = "The specified email address is already registered. "
                + "Each email address may only be used once.";
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new RegistrationError(
                        HttpStatus.CONFLICT.value(),
                        "Email already registered",
                        errorMessage,
                        "/api/v1/auth/register"));
    }

    /**
     * Handles method argument validation failures (e.g. @NotBlank violations).
     * <p>
     * Translates Spring's {@link MethodArgumentNotValidException} into an
     * HTTP 400 Bad Request response with structured validation error messages.
     * Internal stack traces are not exposed.
     *
     * @ex the method argument not valid exception
     * @return a response entity with HTTP 400 status
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException ex) {
        String firstError = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Validation failed");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new RegistrationError(
                        HttpStatus.BAD_REQUEST.value(),
                        "Validation error",
                        firstError,
                        "/api/v1/auth/register"));
    }

    /**
     * Handles illegal argument exceptions from email validation (e.g. malformed email
     * rejected by the {@link EmailAddress} domain value object).
     * <p>
     * Translates {@link IllegalArgumentException} into an HTTP 400 Bad Request
     * response with the validation error message. This catches errors such as null,
     * blank, or malformed email syntax that pass the @NotBlank constraint but are
     * rejected by the domain layer.
     *
     * @param ex the illegal argument exception
     * @return a response entity with HTTP 400 status
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex) {
        String message = ex.getMessage();
        String errorKey = "Validation error";

        // Distinguish between different IllegalArgumentException sources
        if (message != null) {
            if (message.contains("must not be null") || message.contains("must not be blank")) {
                errorKey = "Validation error";
            } else {
                errorKey = "Invalid email format";
            }
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new RegistrationError(
                        HttpStatus.BAD_REQUEST.value(),
                        errorKey,
                        message != null ? message : "Invalid request data",
                        "/api/v1/auth/register"));
    }

    /**
     * Handles any unexpected runtime exceptions, preventing leakage of
     * internal stack traces or database error messages.
     *
     * @param ex the unexpected exception
     * @return a response entity with HTTP 500 status
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntime(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new RegistrationError(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "An unexpected error occurred",
                        "Please try again later. If the problem persists, contact support.",
                        "/api/v1/auth/register"));
    }

    /**
     * A simple error response DTO for registration API errors.
     */
    private static class RegistrationError {
        private final int status;
        private final String error;
        private final String message;
        private final String path;

        RegistrationError(int status, String error, String message, String path) {
            this.status = status;
            this.error = error;
            this.message = message;
            this.path = path;
        }

        public int getStatus() {
            return status;
        }

        public String getError() {
            return error;
        }

        public String getMessage() {
            return message;
        }

        public String getPath() {
            return path;
        }
    }
}