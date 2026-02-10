package com.mrpot.agent.telemetry.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;

/**
 * Entity for tracking processed events (idempotency check).
 * Used to prevent duplicate processing of the same event.
 * 
 * The eventId is computed as: "{runId}:{eventType}:{toolCallId}" for tool events
 * or "{runId}:{eventType}" for run events.
 */
@Data
@Entity
@Table(name = "knowledge_run_event", indexes = {
    @Index(name = "idx_run_event_run_id", columnList = "runId"),
    @Index(name = "idx_run_event_processed_at", columnList = "processedAt")
})
public class KnowledgeRunEventEntity {

    @Id
    @Column(length = 128)
    private String eventId;  // composite key for idempotency

    @Column(length = 36)
    private String runId;

    @Column(length = 50)
    private String eventType;  // run.start, tool.end, etc.

    private Instant processedAt;

    @PrePersist
    protected void onCreate() {
        if (processedAt == null) {
            processedAt = Instant.now();
        }
    }

    /**
     * Create event ID for run events.
     */
    public static String createRunEventId(String runId, String eventType) {
        return runId + ":" + eventType;
    }

    /**
     * Create event ID for tool events.
     */
    public static String createToolEventId(String runId, String eventType, String toolCallId) {
        return runId + ":" + eventType + ":" + toolCallId;
    }
}
