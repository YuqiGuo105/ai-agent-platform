package com.mrpot.agent.telemetry.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * RabbitMQ and JSON configuration for telemetry service.
 * Configures exchanges, queues, and bindings for telemetry events and DLQ.
 */
@Configuration
public class TelemetryAmqpConfig {

    public static final String TELEMETRY_EXCHANGE = "mrpot.telemetry.x";
    public static final String TELEMETRY_QUEUE = "mrpot.telemetry.q";
    public static final String TELEMETRY_DLQ = "mrpot.telemetry.dlq";
    public static final String ROUTING_KEY = "telemetry.#";

    /**
     * ObjectMapper configured for telemetry events.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Handle Java 8 date/time types
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Be lenient with unknown properties
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }

    /**
     * JSON message converter for RabbitMQ.
     */
    @Bean
    public Jackson2JsonMessageConverter jacksonConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * Topic exchange for telemetry events.
     */
    @Bean
    public TopicExchange telemetryExchange() {
        return new TopicExchange(TELEMETRY_EXCHANGE, true, false);
    }

    /**
     * Main telemetry queue with DLQ configuration.
     */
    @Bean
    public Queue telemetryQueue() {
        return QueueBuilder.durable(TELEMETRY_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", TELEMETRY_DLQ)
                .build();
    }

    /**
     * Dead Letter Queue for failed telemetry messages.
     */
    @Bean
    public Queue telemetryDlq() {
        return QueueBuilder.durable(TELEMETRY_DLQ).build();
    }

    /**
     * Binding for telemetry queue to exchange.
     */
    @Bean
    public Binding telemetryBinding(Queue telemetryQueue, TopicExchange telemetryExchange) {
        return BindingBuilder.bind(telemetryQueue).to(telemetryExchange).with(ROUTING_KEY);
    }
}
