package com.nl2db.gateway.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Typed Kafka configuration properties bound from environment variables
 * with NL2DB_KAFKA_ prefix via Spring Boot relaxed binding rules.
 * <p>
 * Maps to Spring Boot's spring.kafka.* properties.
 * Validation is enabled via spring.main.validation.enabled=true and
 * @Validated on this class. Constraints are applied during bean
 * initialization by Spring Boot's ConfigurationPropertiesValidator.
 */
@Component
@ConfigurationProperties(prefix = "app.kafka")
@Validated
public class KafkaProperties {

    private String bootstrapServers;

    /**
     * Kafka broker bootstrap server addresses.
     * Corresponds to environment variable NL2DB_KAFKA_BOOTSTRAP_SERVERS
     * and spring.kafka.bootstrap-servers.
     * <p>
     * Defaults to localhost:9092 for local KRaft development.
     *
     * @return bootstrap server addresses, never null after validation
     */
    @NotNull(message = "Kafka bootstrap servers must not be null")
    public String getBootstrapServers() {
        return bootstrapServers;
    }

    /**
     * Sets the Kafka broker bootstrap server addresses.
     *
     * @param bootstrapServers bootstrap server addresses
     */
    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    @Override
    public String toString() {
        return "KafkaProperties{bootstrapServers='" + bootstrapServers + "'}";
    }
}