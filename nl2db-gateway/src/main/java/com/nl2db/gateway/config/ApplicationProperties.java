package com.nl2db.gateway.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Typed application configuration properties with prefix "app".
 * Properties are bound from environment variables with NL2DB_ prefix
 * via Spring Boot relaxed binding rules (setter-based injection).
 * <p>
 * Validation is enabled via spring.main.validation.enabled=true and
 * @Validated on this class. Constraints are applied during bean
 * initialization by Spring Boot's ConfigurationPropertiesValidator.
 */
@Component
@ConfigurationProperties(prefix = "app")
@Validated
public class ApplicationProperties {

    private String name;
    private String description;
    private String version;

    /**
     * No-arg constructor required by Spring Boot @ConfigurationProperties
     * binder for setter-based injection.
     */
    public ApplicationProperties() {
        // Default constructor
    }

    /**
     * Application name displayed in metrics and logs.
     * <p>
     * Corresponds to environment variable NL2DB_APP_NAME or spring.app.name.
     * Must be non-empty for production use (validated via @NotNull).
     *
     * @return application name, never null after validation
     */
    @NotNull(message = "Application name must not be null")
    @Size(min = 1, message = "Application name must have at least 1 character")
    public String getName() {
        return name;
    }

    /**
     * Sets the application name.
     *
     * @param name Application name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Application human-readable description.
     * <p>
     * Corresponds to environment variable NL2DB_APP_DESCRIPTION or spring.app.description.
     * May be null or empty.
     *
     * @return application description, may be null
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the application description.
     *
     * @param description Application description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Application version following semantic versioning.
     * <p>
     * Corresponds to environment variable NL2DB_APP_VERSION or spring.app.version.
     * Must be non-empty if specified (validated via @Size).
     *
     * @return application version, may be null or empty
     */
    @Size(min = 1, message = "Application version must have at least 1 character if specified")
    public String getVersion() {
        return version;
    }

    /**
     * Sets the application version.
     *
     * @param version Application version
     */
    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "ApplicationProperties{name='" + name + "', description='" + description + "', version='" + version + "'}";
    }
}