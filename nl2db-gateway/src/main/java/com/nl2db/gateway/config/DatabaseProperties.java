package com.nl2db.gateway.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Typed database configuration properties bound from environment variables
 * with NL2DB_DB_ prefix via Spring Boot relaxed binding rules.
 * <p>
 * Maps to Spring Boot's standard spring.datasource.* properties.
 * Validation is enabled via spring.main.validation.enabled=true and
 * @Validated on this class. Constraints are applied during bean
 * initialization by Spring Boot's ConfigurationPropertiesValidator.
 */
@Component
@ConfigurationProperties(prefix = "app.db")
@Validated
public class DatabaseProperties {

    private String url;
    private String username;
    private String password;

    /**
     * No-arg constructor required by Spring Boot @ConfigurationProperties
     * binder for setter-based injection.
     */
    public DatabaseProperties() {
        // Default constructor
    }

    /**
     * JDBC URL for the platform control-plane PostgreSQL database.
     * Corresponds to environment variable NL2DB_DB_URL.
     * <p>
     * Example: jdbc:postgresql://localhost:5432/nl2db
     *
     * @return JDBC URL, never null after validation
     */
    @NotNull(message = "Database URL must not be null")
    public String getUrl() {
        return url;
    }

    /**
     * Sets the JDBC URL for the platform control-plane PostgreSQL database.
     *
     * @param url JDBC URL
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Username for connecting to the platform control-plane PostgreSQL database.
     * Corresponds to environment variable NL2DB_DB_USERNAME.
     * <p>
     * Must be non-empty for production use (validated via @NotNull).
     *
     * @return database username, never null after validation
     */
    @NotNull(message = "Database username must not be null")
    @Size(min = 1, message = "Database username must have at least 1 character")
    public String getUsername() {
        return username;
    }

    /**
     * Sets the username for connecting to the platform control-plane PostgreSQL database.
     *
     * @param username database username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Password for connecting to the platform control-plane PostgreSQL database.
     * Corresponds to environment variable NL2DB_DB_PASSWORD.
     * <p>
     * May be empty for local development with trusted connections.
     *
     * @return database password, may be empty
     */
    @Size(min = 0, message = "Database password length constraints apply if specified")
    public String getPassword() {
        return password;
    }

    /**
     * Sets the password for connecting to the platform control-plane PostgreSQL database.
     *
     * @param password database password
     */
    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "DatabaseProperties{url='" + url + "', username='" + username + "', password='*****'}";
    }
}