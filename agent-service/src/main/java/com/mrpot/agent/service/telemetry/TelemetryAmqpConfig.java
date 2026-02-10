package com.mrpot.agent.service.telemetry;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class TelemetryAmqpConfig {

    public static final String EXCHANGE = "mrpot.telemetry.x";
    public static final String QUEUE = "mrpot.telemetry.q";
    public static final String DLQ = "mrpot.telemetry.dlq";

    @Bean
    public TopicExchange telemetryExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue telemetryQueue() {
        return QueueBuilder.durable(QUEUE)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", EXCHANGE,
                        "x-dead-letter-routing-key", "telemetry.dlq"
                ))
                .build();
    }

    @Bean
    public Queue telemetryDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding bindTelemetry(TopicExchange telemetryExchange, Queue telemetryQueue) {
        return BindingBuilder.bind(telemetryQueue).to(telemetryExchange).with("telemetry.run.*");
    }

    @Bean
    public Binding bindDlq(TopicExchange telemetryExchange, Queue telemetryDlq) {
        return BindingBuilder.bind(telemetryDlq).to(telemetryExchange).with("telemetry.dlq");
    }

    @Bean
    public Jackson2JsonMessageConverter jacksonConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, Jackson2JsonMessageConverter converter) {
        RabbitTemplate t = new RabbitTemplate(cf);
        t.setMessageConverter(converter);
        return t;
    }
}
