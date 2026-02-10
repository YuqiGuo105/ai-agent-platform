package com.mrpot.agent.telemetry.controller;

import com.mrpot.agent.telemetry.entity.TelemetryDlqMessageEntity;
import com.mrpot.agent.telemetry.repository.TelemetryDlqMessageJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST controller for Dead Letter Queue (DLQ) management.
 * Provides endpoints for monitoring, viewing, and replaying failed messages.
 */
@RestController
@RequestMapping("/api/dlq")
@RequiredArgsConstructor
@Tag(name = "DLQ Management", description = "Dead Letter Queue monitoring and replay")
public class DlqController {

    private static final Logger log = LoggerFactory.getLogger(DlqController.class);

    private final TelemetryDlqMessageJpaRepository repo;
    private final RabbitTemplate rabbitTemplate;

    /**
     * List DLQ messages with optional status filter.
     *
     * @param status optional status filter (PENDING/REQUEUED/IGNORED)
     * @return list of DLQ messages
     */
    @GetMapping
    @Operation(
        summary = "List DLQ messages",
        description = "Retrieves all DLQ messages stored in the database. Use the status filter to narrow down results to pending, requeued, or ignored messages."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "DLQ messages retrieved successfully",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = TelemetryDlqMessageEntity.class)))
        )
    })
    public ResponseEntity<List<TelemetryDlqMessageEntity>> listDlqMessages(
            @Parameter(description = "Filter by message status", example = "PENDING", schema = @Schema(allowableValues = {"PENDING", "REQUEUED", "IGNORED"}))
            @RequestParam(required = false) String status) {
        log.debug("Listing DLQ messages with status filter: {}", status);
        
        List<TelemetryDlqMessageEntity> messages;
        if (status != null && !status.isBlank()) {
            messages = repo.findByStatusOrderByReceivedAtDesc(status);
        } else {
            messages = repo.findAll();
        }
        return ResponseEntity.ok(messages);
    }

    /**
     * Get a specific DLQ message by ID.
     *
     * @param id the DLQ message ID
     * @return the DLQ message or 404 if not found
     */
    @GetMapping("/{id}")
    @Operation(
        summary = "Get DLQ message",
        description = "Retrieves a specific DLQ message by its database ID. Includes full payload, headers, and error information."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "DLQ message found and returned",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TelemetryDlqMessageEntity.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "DLQ message not found",
            content = @Content
        )
    })
    public ResponseEntity<TelemetryDlqMessageEntity> getDlqMessage(
            @Parameter(description = "DLQ message database ID", example = "1", required = true)
            @PathVariable Long id) {
        return repo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Requeue a single DLQ message back to the exchange.
     *
     * @param id the DLQ message ID to requeue
     * @return result of the requeue operation
     */
    @PostMapping("/{id}/requeue")
    @Operation(
        summary = "Requeue single DLQ message",
        description = "Republishes a specific DLQ message back to its original exchange and routing key. Updates the message status to REQUEUED and increments the requeue counter."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Message requeued successfully",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"ok\": true, \"id\": 1, \"exchange\": \"mrpot.telemetry.x\", \"routingKey\": \"telemetry.run.start\"}"))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "DLQ message not found",
            content = @Content
        )
    })
    public ResponseEntity<?> requeue(
            @Parameter(description = "DLQ message database ID to requeue", example = "1", required = true)
            @PathVariable Long id) {
        log.info("Requeuing DLQ message: {}", id);
        
        TelemetryDlqMessageEntity entity = repo.findById(id).orElse(null);
        if (entity == null) {
            return ResponseEntity.notFound().build();
        }

        // Determine payload to send
        Object payload = (entity.getPayloadJson() != null && !entity.getPayloadJson().isBlank())
                ? entity.getPayloadJson()
                : entity.getPayloadText();

        // Determine exchange and routing key
        String exchange = (entity.getExchange() == null || entity.getExchange().isBlank())
                ? "mrpot.telemetry.x" : entity.getExchange();
        String routingKey = (entity.getRoutingKey() == null || entity.getRoutingKey().isBlank())
                ? "telemetry.run.unknown" : entity.getRoutingKey();

        // Republish to RabbitMQ
        rabbitTemplate.convertAndSend(exchange, routingKey, payload);
        log.info("Requeued DLQ message {} to exchange={}, routingKey={}", id, exchange, routingKey);

        // Update entity
        entity.setStatus("REQUEUED");
        entity.setRequeueCount(entity.getRequeueCount() + 1);
        entity.setLastRequeueAt(Instant.now());
        repo.save(entity);

        return ResponseEntity.ok(Map.of(
                "ok", true,
                "id", id,
                "exchange", exchange,
                "routingKey", routingKey
        ));
    }

    /**
     * Batch requeue multiple PENDING DLQ messages.
     *
     * @param limit maximum number of messages to requeue (default 100)
     * @return count of successfully requeued messages
     */
    @PostMapping("/requeue")
    @Operation(
        summary = "Batch requeue DLQ messages",
        description = "Requeues multiple PENDING DLQ messages in a single operation. Messages are processed in order of receipt (newest first). Each successfully requeued message is marked as REQUEUED."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Batch requeue completed",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"requeued\": 15}"))
        )
    })
    public ResponseEntity<?> batchRequeue(
            @Parameter(description = "Maximum number of messages to requeue", example = "100")
            @RequestParam(defaultValue = "100") int limit) {
        log.info("Batch requeuing up to {} PENDING DLQ messages", limit);
        
        List<TelemetryDlqMessageEntity> pending = repo.findByStatusOrderByReceivedAtDesc("PENDING");
        int count = 0;

        for (TelemetryDlqMessageEntity entity : pending) {
            if (count >= limit) break;

            try {
                Object payload = (entity.getPayloadJson() != null && !entity.getPayloadJson().isBlank())
                        ? entity.getPayloadJson() : entity.getPayloadText();
                String exchange = (entity.getExchange() == null || entity.getExchange().isBlank())
                        ? "mrpot.telemetry.x" : entity.getExchange();
                String routingKey = (entity.getRoutingKey() == null || entity.getRoutingKey().isBlank())
                        ? "telemetry.run.unknown" : entity.getRoutingKey();

                rabbitTemplate.convertAndSend(exchange, routingKey, payload);

                entity.setStatus("REQUEUED");
                entity.setRequeueCount(entity.getRequeueCount() + 1);
                entity.setLastRequeueAt(Instant.now());
                repo.save(entity);

                count++;
            } catch (Exception e) {
                log.error("Failed to requeue DLQ message {}: {}", entity.getId(), e.getMessage());
            }
        }

        log.info("Batch requeue completed: {} of {} messages requeued", count, pending.size());
        return ResponseEntity.ok(Map.of("requeued", count));
    }

    /**
     * Mark a DLQ message as ignored (won't be requeued in batch operations).
     *
     * @param id the DLQ message ID
     * @return updated entity
     */
    @PostMapping("/{id}/ignore")
    @Operation(
        summary = "Ignore DLQ message",
        description = "Marks a DLQ message as IGNORED. Ignored messages are excluded from batch requeue operations. Use this for messages that should not be retried."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Message marked as ignored",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"ok\": true, \"id\": 1, \"status\": \"IGNORED\"}"))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "DLQ message not found",
            content = @Content
        )
    })
    public ResponseEntity<?> ignore(
            @Parameter(description = "DLQ message database ID to ignore", example = "1", required = true)
            @PathVariable Long id) {
        log.info("Ignoring DLQ message: {}", id);
        
        TelemetryDlqMessageEntity entity = repo.findById(id).orElse(null);
        if (entity == null) {
            return ResponseEntity.notFound().build();
        }

        entity.setStatus("IGNORED");
        repo.save(entity);

        return ResponseEntity.ok(Map.of("ok", true, "id", id, "status", "IGNORED"));
    }

    /**
     * Get DLQ statistics.
     *
     * @return counts by status
     */
    @GetMapping("/stats")
    @Operation(
        summary = "Get DLQ statistics",
        description = "Returns aggregate counts of DLQ messages grouped by status. Useful for monitoring and alerting on failed message volume."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Statistics retrieved successfully",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"total\": 100, \"pending\": 25, \"requeued\": 65, \"ignored\": 10}"))
        )
    })
    public ResponseEntity<?> getStats() {
        long pending = repo.countByStatus("PENDING");
        long requeued = repo.countByStatus("REQUEUED");
        long ignored = repo.countByStatus("IGNORED");
        long total = repo.count();

        return ResponseEntity.ok(Map.of(
                "total", total,
                "pending", pending,
                "requeued", requeued,
                "ignored", ignored
        ));
    }
}
