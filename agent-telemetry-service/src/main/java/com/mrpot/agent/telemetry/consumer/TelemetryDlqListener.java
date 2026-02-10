package com.mrpot.agent.telemetry.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrpot.agent.telemetry.entity.TelemetryDlqMessageEntity;
import com.mrpot.agent.telemetry.repository.TelemetryDlqMessageJpaRepository;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * RabbitMQ listener for Dead Letter Queue (DLQ) messages.
 * Stores failed messages in the database for later analysis and replay.
 * 
 * Features:
 * - Extracts runId, traceId, sessionId from message body if present
 * - Stores message headers and payload for debugging
 * - Manual ACK for reliable processing
 */
@Component
@RequiredArgsConstructor
public class TelemetryDlqListener {

    private static final Logger log = LoggerFactory.getLogger(TelemetryDlqListener.class);

    private final TelemetryDlqMessageJpaRepository repo;
    private final ObjectMapper objectMapper;

    /**
     * Process messages from the dead letter queue.
     * Stores message details in database for later analysis and potential replay.
     */
    @RabbitListener(queues = "mrpot.telemetry.dlq", ackMode = "MANUAL")
    public void onDlq(Message msg, Channel ch) throws Exception {
        long tag = msg.getMessageProperties().getDeliveryTag();

        try {
            TelemetryDlqMessageEntity entity = new TelemetryDlqMessageEntity();
            entity.setReceivedAt(Instant.now());
            entity.setExchange(msg.getMessageProperties().getReceivedExchange());
            entity.setRoutingKey(msg.getMessageProperties().getReceivedRoutingKey());

            // Store headers as JSON string
            try {
                entity.setHeaders(objectMapper.writeValueAsString(msg.getMessageProperties().getHeaders()));
            } catch (Exception e) {
                log.warn("Failed to serialize message headers: {}", e.getMessage());
            }

            // Extract error info from headers if present (standard DLQ headers)
            var headers = msg.getMessageProperties().getHeaders();
            if (headers != null) {
                if (headers.get("x-first-death-reason") != null) {
                    entity.setErrorType(String.valueOf(headers.get("x-first-death-reason")));
                }
                if (headers.get("x-exception-message") != null) {
                    entity.setErrorMsg(truncate(String.valueOf(headers.get("x-exception-message")), 2000));
                }
            }

            byte[] body = msg.getBody();
            String bodyText = new String(body, StandardCharsets.UTF_8);

            // Try to parse as JSON
            try {
                JsonNode node = objectMapper.readTree(bodyText);
                entity.setPayloadJson(bodyText);

                // Extract runId, traceId, sessionId if present
                if (node.hasNonNull("runId")) {
                    entity.setRunId(node.get("runId").asText());
                }
                if (node.hasNonNull("traceId")) {
                    entity.setTraceId(node.get("traceId").asText());
                }
                if (node.hasNonNull("sessionId")) {
                    entity.setSessionId(node.get("sessionId").asText());
                }
            } catch (Exception e) {
                // Not valid JSON, store as text
                entity.setPayloadText(truncate(bodyText, 20000));
                log.debug("DLQ message body is not valid JSON, stored as text");
            }

            repo.save(entity);
            log.info("Saved DLQ message with ID: {}, runId: {}", entity.getId(), entity.getRunId());
            ch.basicAck(tag, false);
        } catch (Exception e) {
            log.error("Error processing DLQ message: {}", e.getMessage(), e);
            // Reject without requeue to avoid infinite loop
            ch.basicNack(tag, false, false);
        }
    }

    /**
     * Truncate string to max length, adding truncation indicator if needed.
     */
    private static String truncate(String s, int maxLength) {
        if (s == null) return null;
        return s.length() <= maxLength ? s : s.substring(0, maxLength - 15) + " ...[truncated]";
    }
}
