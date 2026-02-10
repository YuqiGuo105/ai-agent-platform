package com.mrpot.agent.telemetry.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;

/**
 * Outbox table for reliable Elasticsearch indexing.
 * Uses the transactional outbox pattern to ensure data consistency
 * between PostgreSQL and Elasticsearch.
 */
@Data
@Entity
@Table(name = "es_outbox", indexes = {
    @Index(name = "idx_es_outbox_status_retry", columnList = "status, nextRetryAt"),
    @Index(name = "idx_es_outbox_run_id", columnList = "runId")
})
public class EsOutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 36)
    private String runId;

    @Column(length = 100, nullable = false)
    private String indexName;  // mrpot_runs or mrpot_tool_calls

    @Column(length = 128, nullable = false)
    private String docId;  // document ID in ES (runId or toolCallId)

    @Column(columnDefinition = "TEXT", nullable = false)
    private String docJson;  // document content as JSON

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private OutboxStatus status = OutboxStatus.PENDING;

    private Integer retryCount = 0;

    private Instant nextRetryAt;

    @Column(length = 500)
    private String lastError;

    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (nextRetryAt == null) {
            nextRetryAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Outbox entry status.
     */
    public enum OutboxStatus {
        PENDING,  // ready to be sent
        SENT,     // successfully indexed
        FAILED    // permanently failed after max retries
    }

    /**
     * Calculate next retry time using exponential backoff.
     * 
     * @param retryCount current retry count
     * @return next retry time
     */
    public static Instant calculateNextRetry(int retryCount) {
        // Exponential backoff: 1s, 2s, 4s, 8s, 16s, 32s...
        long delaySeconds = (long) Math.pow(2, Math.min(retryCount, 6));
        return Instant.now().plusSeconds(delaySeconds);
    }
}
