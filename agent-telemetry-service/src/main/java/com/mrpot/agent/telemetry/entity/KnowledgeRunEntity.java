package com.mrpot.agent.telemetry.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;

@Data
@Entity
@Table(name="knowledge_run")
public class KnowledgeRunEntity {

    @Id
    private String id;

    private Instant createdAt;
    private Instant updatedAt;

    private String traceId;
    private String sessionId;
    private String userId;

    private String mode;
    private String model;

    @Column(length=4000)
    private String question;

    @Column(length=12000)
    private String answerFinal;

    private Integer kbHitCount;

    @Column(length=2000)
    private String kbDocIds;

    private Integer historyCount;

    @Column(length = 4000)
    private String recentQuestionsJson;  // JSON array of recent user question strings

    private Long kbLatencyMs;
    private Long totalLatencyMs;

    private String status;   // RUNNING/DONE/FAILED/CANCELLED
    private String errorCode;

    @Column(length = 36)
    private String parentRunId;

    @Column(length = 20)
    private String replayMode;

    private Double complexityScore;

    @Column(length = 20)
    private String executionMode;  // "FAST" or "DEEP"

    private Integer deepRoundsUsed;

    private Integer toolCallsCount;

    private Double toolSuccessRate;

    @Column(columnDefinition = "TEXT")
    private String featureBreakdownJson;
}
