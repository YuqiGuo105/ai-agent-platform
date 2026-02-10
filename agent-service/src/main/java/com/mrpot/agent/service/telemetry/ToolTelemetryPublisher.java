package com.mrpot.agent.service.telemetry;

import com.mrpot.agent.common.telemetry.ToolTelemetryEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publisher for tool call telemetry events.
 * Sends events to RabbitMQ for async processing by telemetry-service.
 * 
 * CRITICAL: All failures are caught and logged - never affects tool execution.
 */
@Component
@RequiredArgsConstructor
public class ToolTelemetryPublisher {

    private static final Logger log = LoggerFactory.getLogger(ToolTelemetryPublisher.class);
    
    private final RabbitTemplate rabbitTemplate;

    /**
     * Publish a tool telemetry event.
     * 
     * @param event the event to publish
     */
    public void publish(ToolTelemetryEvent event) {
        try {
            String routingKey = routingKey(event.type());
            rabbitTemplate.convertAndSend(TelemetryAmqpConfig.EXCHANGE, routingKey, event);
            log.debug("Published tool telemetry: type={}, toolCallId={}", 
                event.type(), event.toolCallId());
        } catch (Exception e) {
            // CRITICAL: Never affect the main tool call path
            log.warn("Tool telemetry publish failed: type={}, error={}", 
                event.type(), e.getMessage());
        }
    }

    /**
     * Publish event asynchronously (fire-and-forget).
     * Used for non-blocking telemetry in hot path.
     */
    public void publishAsync(ToolTelemetryEvent event) {
        // Use virtual thread for non-blocking publish
        Thread.startVirtualThread(() -> publish(event));
    }

    private String routingKey(String type) {
        // tool.start -> telemetry.tool.start
        // tool.end -> telemetry.tool.end
        // tool.error -> telemetry.tool.error
        String suffix = type == null ? "unknown" : type.replace("tool.", "");
        return "telemetry.tool." + suffix;
    }
}
