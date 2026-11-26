package com.nl2db.gateway.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Typed Redis configuration properties bound from environment variables
 * with NL2DB_REDIS_ prefix via Spring Boot relaxed binding rules.
 * <p>
 * Maps to Spring Data Redis spring.data.redis.* properties.
 * Validation is enabled via spring.main.validation.enabled=true and
 * @Validated on this class. Constraints are applied during bean
 * initialization by Spring Boot's ConfigurationPropertiesValidator.
 */
@Component
@ConfigurationProperties(prefix = "app.redis")
@Validated
public class RedisProperties {

    private String host;
    private int port;
    private String password;
    private int database;

    /**
     * No-arg constructor required by Spring Boot @ConfigurationProperties
     * binder for setter-based injection.
     */
    public RedisProperties() {
        // Default constructor
    }

    /**
     * Redis server hostname or IP address.
     * Corresponds to environment variable NL2DB_REDIS_HOST.
     * <p>
     * Defaults to localhost for local development.
     *
     * @return Redis hostname, never null after validation
     */
    @NotNull(message = "Redis host must not be null")
    public String getHost() {
        return host;
    }

    /**
     * Sets the Redis server hostname or IP address.
     *
     * @param host Redis hostname
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * Redis server port number.
     * Corresponds to environment variable NL2DB_REDIS_PORT.
     * <p>
     * Defaults to 6379 for local development.
     *
     * @return Redis port, >= 1
     */
    @NotNull(message = "Redis port must not be null")
    public int getPort() {
        return port;
    }

    /**
     * Sets the Redis server port number.
     *
     * @param port Redis port
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Redis authentication password.
     * Corresponds to environment variable NL2DB_REDIS_PASSWORD.
     * <p>
     * May be empty for local development with no authentication.
     *
     * @return Redis password, may be empty
     */
    @Size(min = 0, message = "Redis password length constraints apply if specified")
    public String getPassword() {
        return password;
    }

    /**
     * Sets the Redis authentication password.
     *
     * @param password Redis password
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Redis database index.
     * Corresponds to environment variable NL2DB_REDIS_DATABASE.
     * <p>
     * Defaults to 0 (default database).
     *
     * @return Redis database index, >= 0
     */
    @NotNull(message = "Redis database must not be null")
    public int getDatabase() {
        return database;
    }

    /**
     * Sets the Redis database index.
     *
     * @param database Redis database index
     */
    public void setDatabase(int database) {
        this.database = database;
    }

    @Override
    public String toString() {
        return "RedisProperties{host='" + host + "' port=" + port +
                " password='*****' database=" + database + "}";
    }
}