package com.mrpot.agent.telemetry.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * Entity for storing Dead Letter Queue (DLQ) messages.
 * Messages that fail processing are stored here for later analysis and replay.
 */
@Data
@Entity
@Table(name = "telemetry_dlq_message", indexes = {
    @Index(name = "idx_dlq_status", columnList = "status"),
    @Index(name = "idx_dlq_received_at", columnList = "receivedAt"),
    @Index(name = "idx_dlq_run_id", columnList = "runId")
})
public class TelemetryDlqMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant receivedAt;

    @Column(length = 255)
    private String exchange;

    @Column(length = 255)
    private String routingKey;

    @Column(columnDefinition = "jsonb")
    private String headers;  // Store message headers as JSON string

    @Column(columnDefinition = "jsonb")
    private String payloadJson;  // Store payload as JSON if valid JSON

    @Column(length = 20000)
    private String payloadText;  // Store payload as text if not valid JSON

    @Column(length = 255)
    private String errorType;

    @Column(length = 2000)
    private String errorMsg;

    // Optional extracted fields for easier querying
    @Column(length = 36)
    private String runId;

    @Column(length = 36)
    private String traceId;

    @Column(length = 36)
    private String sessionId;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";  // PENDING/REQUEUED/IGNORED

    @Column(nullable = false)
    private Integer requeueCount = 0;

    private Instant lastRequeueAt;

    @PrePersist
    protected void onCreate() {
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
        if (status == null) {
            status = "PENDING";
        }
        if (requeueCount == null) {
            requeueCount = 0;
        }
    }
}
