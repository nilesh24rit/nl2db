package com.nl2db.gateway.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

import java.time.Duration;

/**
 * MongoDB client configuration.
 * <p>
 * Creates a single MongoClient bean configured via typed MongoDbProperties.
 * The client is initialized on startup and closed cleanly on bean destruction.
 * <p>
 * <b>Security:</b> MongoDB URIs containing credentials are never logged.
 * Redacted toobox in toString() and health-indicator output.
 */
@Configuration
public class MongoDbConfig {

    private final MongoDbProperties mongoDbProperties;

    private MongoClient mongoClient;

    @Autowired
    public MongoDbConfig(MongoDbProperties mongoDbProperties) {
        this.mongoDbProperties = mongoDbProperties;
    }

    /**
     * Creates and configures a MongoClient instance.
     * <p>
     * Reads the connection URI from MongoDbProperties. Validates that the URI
     * is non-empty. Creates the client via the driver's factory method which
     * properly parses the URI, extracts hosts, credentials, and options.
     * <p>
     * <b>Security:</b> The raw URI is not stored in logs. The toString() method
     * redacts the URI password component.
     */
    @Bean
    public MongoClient mongoClient() {
        String uri = mongoDbProperties.getUri();
        Assert.notNull(uri, "MongoDB URI must not be null");
        Assert.hasText(uri, "MongoDB URI must not be empty");

        mongoClient = MongoClients.create(uri);
        return mongoClient;
    }

    /**
     * Ensures the MongoClient is closed cleanly when the Spring context shuts down.
     * <p>
     * This method is invoked by Spring's destruction lifecycle for beans defined
     * in the @Configuration class. It ensures that all underlying connections are
     * closed and resources are released.
     */
    @PreDestroy
    public void closeMongoClient() {
        if (mongoClient != null) {
            mongoClient.close();
            mongoClient = null;
        }
    }
}