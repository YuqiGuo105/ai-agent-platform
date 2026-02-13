package com.mrpot.agent.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommandAmqpConfig {

    public static final String COMMAND_EXCHANGE = "mrpot.command.x";
    public static final String AGENT_COMMAND_QUEUE = "mrpot.agent.cmd.q";
    public static final String REPLAY_ROUTING_KEY = "agent.replay.request";

    @Bean
    public TopicExchange commandExchange() {
        return new TopicExchange(COMMAND_EXCHANGE, true, false);
    }

    @Bean
    public Queue agentCommandQueue() {
        return new Queue(AGENT_COMMAND_QUEUE, true);
    }

    @Bean
    public Binding replayBinding(Queue agentCommandQueue, TopicExchange commandExchange) {
        return BindingBuilder.bind(agentCommandQueue)
            .to(commandExchange)
            .with(REPLAY_ROUTING_KEY);
    }
}
