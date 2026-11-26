package com.nl2db.gateway.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Typed MongoDB configuration properties bound from environment variables
 * with NL2DB_MONGODB_ prefix via Spring Boot relaxed binding rules.
 * <p>
 * Maps to MongoDB driver connection settings.
 * Validation is enabled via spring.main.validation.enabled=true and
 * @Validated on this class. Constraints are applied during bean
 * initialization by Spring Boot's ConfigurationPropertiesValidator.
 */
@Component
@ConfigurationProperties(prefix = "app.mongodb")
@Validated
public class MongoDbProperties {

    private String uri;
    private String database;

    /**
     * No-arg constructor required by Spring Boot @ConfigurationProperties
     * binder for setter-based injection.
     */
    public MongoDbProperties() {
        // Default constructor
    }

    /**
     * MongoDB connection URI.
     * Corresponds to environment variable NL2DB_MONGODB_URI.
     * <p>
     * Example: mongodb://localhost:27017
     * <p>
     * <b>Security:</b> This URI may contain credentials. Never log this value
     * directly. Redact before including in any health or diagnostic output.
     *
     * @return MongoDB URI, may be null if not configured (fail-fast in production)
     */
    @NotNull(message = "MongoDB URI must not be null")
    public String getUri() {
        return uri;
    }

    /**
     * Sets the MongoDB connection URI.
     *
     * @param uri MongoDB URI
     */
    public void setUri(String uri) {
        this.uri = uri;
    }

    /**
     * Default MongoDB database name.
     * Corresponds to environment variable NL2DB_MONGODB_DATABASE.
     * <p>
     * Defaults to "nl2db" if not specified.
     *
     * @return database name, never null after validation
     */
    @NotNull(message = "MongoDB database must not be null")
    public String getDatabase() {
        return database;
    }

    /**
     * Sets the default MongoDB database name.
     *
     * @param database database name
     */
    public void setDatabase(String database) {
        this.database = database;
    }

    @Override
    public String toString() {
        return "MongoDbProperties{uri='*****', database='" + database + "'}";
    }
}