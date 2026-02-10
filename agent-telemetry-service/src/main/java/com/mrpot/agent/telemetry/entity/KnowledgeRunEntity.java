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

    private Long kbLatencyMs;
    private Long totalLatencyMs;

    private String status;   // RUNNING/DONE/FAILED/CANCELLED
    private String errorCode;
}
