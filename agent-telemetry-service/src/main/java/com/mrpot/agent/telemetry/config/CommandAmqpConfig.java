package com.mrpot.agent.telemetry.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommandAmqpConfig {

    public static final String COMMAND_EXCHANGE = "mrpot.command.x";
    public static final String REPLAY_ROUTING_KEY = "agent.replay.request";

    @Bean
    public TopicExchange commandExchange() {
        return new TopicExchange(COMMAND_EXCHANGE, true, false);
    }
}
