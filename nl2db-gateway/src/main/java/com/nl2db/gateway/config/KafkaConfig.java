package com.nl2db.gateway.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer and consumer configuration for the gateway.
 * <p>
 * Relies on Spring Boot's Kafka auto-configuration for baseline settings
 * (bootstrap servers, serializers, timeouts) and supplements with
 * explicit producer/consumer factory beans driven by typed configuration.
 * Broker connection settings are driven by KafkaProperties via
 * relaxed binding (NL2DB_KAFKA_BOOTSTRAP_SERVERS environment variable).
 */
@Configuration
public class KafkaConfig {

    private final KafkaProperties kafkaProperties;

    public KafkaConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    /**
     * ProducerFactory with String keys and JSON value serialization.
     * Bootstrap servers driven by typed configuration.
     * Caller must call .get() on the Future to observe send failures.
     */
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();

        // Bootstrap servers from typed configuration
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaProperties.getBootstrapServers());

        // StringSerializer for keys, JsonSerializer for values
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Producer reliability: acknowledge all replicas (not fire-and-forget)
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // Retries on transient failures (bounded, not infinite)
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);

        // Do not silently ignore producer failures
        // (caller must call .get() on the Future or listen on completion)

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * KafkaTemplate configured with the producer factory.
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * ConsumerFactory with String deserialization.
     * Spring Boot's auto-configuration handles the deserializer setup;
     * this factory overrides with explicit settings from typed configuration.
     * Consumer group identification via NL2DB_KAFKA_CONSUMER_GROUP environment variable.
     * Offset handling: explicit control, auto-commit disabled.
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();

        // Bootstrap servers from typed configuration
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaProperties.getBootstrapServers());

        // Consumer group identification
        String consumerGroup = System.getenv("NL2DB_KAFKA_CONSUMER_GROUP");
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroup != null ? consumerGroup : "nl2db-gateway");

        // Offset handling: explicit control, do not auto-commit
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    /**
     * Concurrent Kafka listener container factory with manual offset control.
     * Uses manual acknowledgment mode so that processing failures are
     * not silently treated as successful. The listener must explicitly
     * commit offsets after successful processing.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String>
    kafkaListenerContainerFactory(ConsumerFactory<String, String> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL);

        return factory;
    }
}