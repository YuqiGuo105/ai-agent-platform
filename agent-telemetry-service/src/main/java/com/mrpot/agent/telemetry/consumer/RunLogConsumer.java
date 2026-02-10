package com.mrpot.agent.telemetry.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrpot.agent.common.telemetry.RunLogEnvelope;
import com.mrpot.agent.common.telemetry.ToolTelemetryEvent;
import com.mrpot.agent.telemetry.service.TelemetryProjector;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ consumer for telemetry events.
 * Handles both run events and tool events via unified message handler.
 * 
 * Features:
 * - Manual ACK for reliable processing
 * - Configurable concurrency (2-4 consumers)
 * - Automatic DLQ routing on failure
 * - Smart message type detection
 */
@Component
@RequiredArgsConstructor
public class RunLogConsumer {

    private static final Logger log = LoggerFactory.getLogger(RunLogConsumer.class);
    
    private final TelemetryProjector projector;
    private final ObjectMapper objectMapper;

    /**
     * Unified consumer for all telemetry events.
     * Detects message type from content and routes to appropriate handler.
     */
    @RabbitListener(
        queues = "mrpot.telemetry.q",
        ackMode = "MANUAL",
        concurrency = "2-4"
    )
    public void onMessage(Message msg, Channel ch) throws Exception {
        long tag = msg.getMessageProperties().getDeliveryTag();
        String body = new String(msg.getBody());
        
        try {
            // Parse JSON to determine message type
            JsonNode json = objectMapper.readTree(body);
            String type = json.has("type") ? json.get("type").asText() : "";
            
            boolean processed;
            if (type.startsWith("tool.")) {
                // Tool telemetry event
                ToolTelemetryEvent event = objectMapper.treeToValue(json, ToolTelemetryEvent.class);
                processed = projector.processToolEvent(event);
                log.debug("Tool event processed={}: type={}, toolCallId={}", 
                    processed, event.type(), event.toolCallId());
            } else if (type.startsWith("run.")) {
                // Run telemetry event
                RunLogEnvelope env = objectMapper.treeToValue(json, RunLogEnvelope.class);
                processed = projector.processRunEvent(env);
                log.debug("Run event processed={}: type={}, runId={}", 
                    processed, env.type(), env.runId());
            } else {
                log.warn("Unknown event type: {}", type);
                processed = false;
            }
            
            ch.basicAck(tag, false);
        } catch (Exception ex) {
            log.error("Failed to process telemetry event: error={}", ex.getMessage(), ex);
            // Reject and route to DLQ (no requeue to avoid infinite loop)
            ch.basicNack(tag, false, false);
        }
    }
}
