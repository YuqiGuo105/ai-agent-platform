package com.mrpot.agent.service.telemetry;

import com.mrpot.agent.common.telemetry.RunLogEnvelope;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RunLogPublisher {

    private static final Logger log = LoggerFactory.getLogger(RunLogPublisher.class);
    private final RabbitTemplate rabbitTemplate;

    public void publish(RunLogEnvelope env) {
        try {
            String rk = routingKey(env.type());
            rabbitTemplate.convertAndSend(TelemetryAmqpConfig.EXCHANGE, rk, env);
        } catch (Exception e) {
            // 绝不影响主回答链路
            log.warn("telemetry publish failed: {}", e.toString());
        }
    }

    private String routingKey(String type) {
        // run.start -> telemetry.run.start
        // run.rag_done -> telemetry.run.rag_done
        String suffix = type == null ? "unknown" : type.replace("run.", "");
        return "telemetry.run." + suffix;
    }
}
