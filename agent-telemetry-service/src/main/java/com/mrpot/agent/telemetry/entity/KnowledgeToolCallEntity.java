package com.mrpot.agent.telemetry.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;

/**
 * Entity for storing individual tool call telemetry.
 * One record per tool.end or tool.error event received.
 */
@Data
@Entity
@Table(name = "knowledge_tool_call", indexes = {
    @Index(name = "idx_tool_call_run_id", columnList = "runId"),
    @Index(name = "idx_tool_call_tool_name", columnList = "toolName"),
    @Index(name = "idx_tool_call_called_at", columnList = "calledAt")
})
public class KnowledgeToolCallEntity {

    @Id
    @Column(length = 36)
    private String id;  // toolCallId from event

    @Column(length = 36)
    private String runId;  // FK to knowledge_run

    @Column(length = 100, nullable = false)
    private String toolName;

    private Integer attempt;  // retry attempt number

    private Boolean ok;  // whether call succeeded

    private Long durationMs;  // execution duration

    @Column(length = 64)
    private String argsDigest;  // SHA-256 hash of args

    @Column(length = 512)
    private String argsPreview;  // truncated/redacted args preview

    private Integer argsSize;  // original args size in bytes

    @Column(length = 64)
    private String resultDigest;  // SHA-256 hash of result

    @Column(length = 512)
    private String resultPreview;  // truncated/redacted result preview

    private Integer resultSize;  // result size in bytes

    private Boolean cacheHit;

    private Long ttlHintSeconds;

    @Column(length = 20)
    private String freshness;  // FRESH / STALE / MISS

    @Column(length = 50)
    private String errorCode;  // error type/code

    @Column(length = 500)
    private String errorMsg;  // error message (sanitized)

    private Boolean retryable;  // whether error is retryable

    @Column(columnDefinition = "TEXT")
    private String keyInfoJson;  // extracted key info as JSON

    private Instant calledAt;  // when the call was made

    private Instant createdAt;  // when this record was created

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
