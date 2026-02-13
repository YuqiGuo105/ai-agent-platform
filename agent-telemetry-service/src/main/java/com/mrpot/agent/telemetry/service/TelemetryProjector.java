package com.mrpot.agent.telemetry.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrpot.agent.common.telemetry.RunLogEnvelope;
import com.mrpot.agent.common.telemetry.ToolTelemetryData;
import com.mrpot.agent.common.telemetry.ToolTelemetryEvent;
import com.mrpot.agent.telemetry.entity.*;
import com.mrpot.agent.telemetry.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Core projector service that processes telemetry events.
 * 
 * Key features:
 * - Idempotent processing via KnowledgeRunEvent table
 * - Status progression rules (DONE not overwritten by RUNNING, etc.)
 * - Transactional outbox pattern for ES indexing
 */
@Service
@RequiredArgsConstructor
public class TelemetryProjector {

    private static final Logger log = LoggerFactory.getLogger(TelemetryProjector.class);
    
    public static final String ES_INDEX_RUNS = "mrpot_runs";
    public static final String ES_INDEX_TOOL_CALLS = "mrpot_tool_calls";
    
    // Status priority: higher number = can't be overwritten by lower
    private static final Map<String, Integer> STATUS_PRIORITY = Map.of(
        "RUNNING", 1,
        "DONE", 2,
        "FAILED", 3,
        "CANCELLED", 3
    );

    private final KnowledgeRunJpaRepository runRepo;
    private final KnowledgeToolCallJpaRepository toolCallRepo;
    private final KnowledgeRunEventJpaRepository eventRepo;
    private final EsOutboxJpaRepository outboxRepo;
    private final ObjectMapper objectMapper;

    /**
     * Process a run telemetry event.
     * 
     * @param envelope the run event
     * @return true if processed, false if duplicate
     */
    @Transactional
    public boolean processRunEvent(RunLogEnvelope envelope) {
        String eventId = KnowledgeRunEventEntity.createRunEventId(
            envelope.runId(), envelope.type());
        
        // Idempotency check
        if (!tryRecordEvent(eventId, envelope.runId(), envelope.type())) {
            log.debug("Duplicate event skipped: {}", eventId);
            return false;
        }
        
        // Process based on event type
        switch (envelope.type()) {
            case "run.start" -> processRunStart(envelope);
            case "run.rag_done" -> processRagDone(envelope);
            case "run.final" -> processRunFinal(envelope);
            case "run.failed" -> processRunFailed(envelope);
            case "run.cancelled" -> processRunCancelled(envelope);
            default -> log.warn("Unknown run event type: {}", envelope.type());
        }
        
        return true;
    }

    /**
     * Process a tool telemetry event.
     * 
     * @param event the tool event
     * @return true if processed, false if duplicate
     */
    @Transactional
    public boolean processToolEvent(ToolTelemetryEvent event) {
        String eventId = KnowledgeRunEventEntity.createToolEventId(
            event.runId(), event.type(), event.toolCallId());
        
        // Idempotency check
        if (!tryRecordEvent(eventId, event.runId(), event.type())) {
            log.debug("Duplicate tool event skipped: {}", eventId);
            return false;
        }
        
        // Only process tool.end and tool.error (tool.start is informational)
        switch (event.type()) {
            case ToolTelemetryEvent.TYPE_END -> processToolEnd(event);
            case ToolTelemetryEvent.TYPE_ERROR -> processToolError(event);
            case ToolTelemetryEvent.TYPE_START -> {
                // Just record for idempotency, no entity creation
                log.debug("Tool start recorded: {}", event.toolCallId());
            }
            default -> log.warn("Unknown tool event type: {}", event.type());
        }
        
        return true;
    }

    /**
     * Try to record an event for idempotency.
     * Returns false if already processed.
     */
    private boolean tryRecordEvent(String eventId, String runId, String eventType) {
        try {
            KnowledgeRunEventEntity entity = new KnowledgeRunEventEntity();
            entity.setEventId(eventId);
            entity.setRunId(runId);
            entity.setEventType(eventType);
            entity.setProcessedAt(Instant.now());
            eventRepo.save(entity);
            return true;
        } catch (DataIntegrityViolationException e) {
            // Duplicate key - event already processed
            return false;
        }
    }

    private void processRunStart(RunLogEnvelope e) {
        KnowledgeRunEntity run = runRepo.findById(e.runId()).orElseGet(() -> {
            KnowledgeRunEntity n = new KnowledgeRunEntity();
            n.setId(e.runId());
            n.setCreatedAt(Instant.now());
            return n;
        });

        // Only update if status is not already higher priority
        if (canUpdateStatus(run.getStatus(), "RUNNING")) {
            run.setUpdatedAt(Instant.now());
            run.setTraceId(e.traceId());
            run.setSessionId(e.sessionId());
            run.setUserId(e.userId());
            run.setMode(e.mode());
            run.setModel(e.model());
            run.setQuestion(trunc((String) e.data().getOrDefault("question", ""), 3800));
            run.setStatus("RUNNING");

            Object parentRunId = e.data().get("parentRunId");
            if (parentRunId instanceof String pid && !pid.isBlank()) {
                run.setParentRunId(pid);
            }
            Object replayMode = e.data().get("replayMode");
            if (replayMode instanceof String rm && !rm.isBlank()) {
                run.setReplayMode(rm);
            }

            runRepo.save(run);
            
            // Add to ES outbox
            addToOutbox(ES_INDEX_RUNS, e.runId(), run);
        }
    }

    private void processRagDone(RunLogEnvelope e) {
        runRepo.findById(e.runId()).ifPresent(run -> {
            run.setUpdatedAt(Instant.now());
            run.setKbHitCount(toInt(e.data().get("kbHitCount"), 0));
            run.setKbLatencyMs(toLong(e.data().get("kbLatencyMs"), 0L));

            Object ids = e.data().get("kbDocIds");
            if (ids instanceof List<?> list) {
                run.setKbDocIds(String.join(",", list.stream().map(String::valueOf).toList()));
            }
            runRepo.save(run);
            
            // Update ES outbox
            addToOutbox(ES_INDEX_RUNS, e.runId(), run);
        });
    }

    private void processRunFinal(RunLogEnvelope e) {
        runRepo.findById(e.runId()).ifPresent(run -> {
            if (canUpdateStatus(run.getStatus(), "DONE")) {
                run.setUpdatedAt(Instant.now());
                run.setAnswerFinal(trunc((String) e.data().getOrDefault("answerFinal", ""), 11000));
                run.setTotalLatencyMs(toLong(e.data().get("totalLatencyMs"), 0L));
                run.setStatus("DONE");

                Object parentRunId = e.data().get("parentRunId");
                if (parentRunId instanceof String pid && !pid.isBlank() && run.getParentRunId() == null) {
                    run.setParentRunId(pid);
                }

                runRepo.save(run);
                
                // Update ES outbox
                addToOutbox(ES_INDEX_RUNS, e.runId(), run);
            }
        });
    }

    private void processRunFailed(RunLogEnvelope e) {
        runRepo.findById(e.runId()).ifPresent(run -> {
            if (canUpdateStatus(run.getStatus(), "FAILED")) {
                run.setUpdatedAt(Instant.now());
                run.setStatus("FAILED");
                run.setErrorCode(trunc(String.valueOf(e.data().getOrDefault("errorCode", "UNKNOWN")), 120));
                runRepo.save(run);
                
                // Update ES outbox
                addToOutbox(ES_INDEX_RUNS, e.runId(), run);
            }
        });
    }

    private void processRunCancelled(RunLogEnvelope e) {
        runRepo.findById(e.runId()).ifPresent(run -> {
            if (canUpdateStatus(run.getStatus(), "CANCELLED")) {
                run.setUpdatedAt(Instant.now());
                run.setStatus("CANCELLED");
                runRepo.save(run);
                
                // Update ES outbox
                addToOutbox(ES_INDEX_RUNS, e.runId(), run);
            }
        });
    }

    private void processToolEnd(ToolTelemetryEvent e) {
        ToolTelemetryData data = e.data();
        
        KnowledgeToolCallEntity call = new KnowledgeToolCallEntity();
        call.setId(e.toolCallId());
        call.setRunId(e.runId());
        call.setToolName(data.toolName());
        call.setAttempt(data.attempt());
        call.setOk(true);
        call.setDurationMs(data.durationMs());
        call.setArgsDigest(data.argsDigest());
        call.setArgsPreview(data.argsPreview());
        call.setArgsSize(data.argsSize());
        call.setResultDigest(data.resultDigest());
        call.setResultPreview(data.resultPreview());
        call.setResultSize(data.resultSize());
        call.setCacheHit(data.cacheHit());
        call.setTtlHintSeconds(data.ttlHintSeconds());
        call.setFreshness(data.freshness());
        call.setCalledAt(e.ts());
        
        // Serialize key info as JSON
        if (data.keyInfo() != null && !data.keyInfo().isEmpty()) {
            try {
                call.setKeyInfoJson(objectMapper.writeValueAsString(data.keyInfo()));
            } catch (JsonProcessingException ex) {
                log.warn("Failed to serialize keyInfo: {}", ex.getMessage());
            }
        }
        
        toolCallRepo.save(call);
        
        // Add to ES outbox
        addToOutbox(ES_INDEX_TOOL_CALLS, e.toolCallId(), call);
    }

    private void processToolError(ToolTelemetryEvent e) {
        ToolTelemetryData data = e.data();
        
        KnowledgeToolCallEntity call = new KnowledgeToolCallEntity();
        call.setId(e.toolCallId());
        call.setRunId(e.runId());
        call.setToolName(data.toolName());
        call.setAttempt(data.attempt());
        call.setOk(false);
        call.setDurationMs(data.durationMs());
        call.setArgsDigest(data.argsDigest());
        call.setArgsPreview(data.argsPreview());
        call.setArgsSize(data.argsSize());
        call.setErrorCode(data.errorCode());
        call.setErrorMsg(trunc(data.errorMessage(), 500));
        call.setRetryable(data.retryable());
        call.setCalledAt(e.ts());
        
        // Serialize key info as JSON
        if (data.keyInfo() != null && !data.keyInfo().isEmpty()) {
            try {
                call.setKeyInfoJson(objectMapper.writeValueAsString(data.keyInfo()));
            } catch (JsonProcessingException ex) {
                log.warn("Failed to serialize keyInfo: {}", ex.getMessage());
            }
        }
        
        toolCallRepo.save(call);
        
        // Add to ES outbox
        addToOutbox(ES_INDEX_TOOL_CALLS, e.toolCallId(), call);
    }

    /**
     * Check if status can be updated based on priority rules.
     */
    private boolean canUpdateStatus(String currentStatus, String newStatus) {
        if (currentStatus == null) return true;
        int currentPriority = STATUS_PRIORITY.getOrDefault(currentStatus, 0);
        int newPriority = STATUS_PRIORITY.getOrDefault(newStatus, 0);
        return newPriority >= currentPriority;
    }

    /**
     * Add an entity to the ES outbox for later indexing.
     */
    private void addToOutbox(String indexName, String docId, Object entity) {
        try {
            EsOutboxEntity outbox = new EsOutboxEntity();
            outbox.setRunId(entity instanceof KnowledgeRunEntity run ? run.getId() :
                           entity instanceof KnowledgeToolCallEntity call ? call.getRunId() : null);
            outbox.setIndexName(indexName);
            outbox.setDocId(docId);
            outbox.setDocJson(objectMapper.writeValueAsString(entity));
            outbox.setStatus(EsOutboxEntity.OutboxStatus.PENDING);
            outbox.setRetryCount(0);
            outbox.setNextRetryAt(Instant.now());
            outboxRepo.save(outbox);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize entity for ES outbox: {}", e.getMessage());
        }
    }

    // Utility methods

    private static String trunc(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + " ...[truncated]";
    }

    private static int toInt(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v)); } 
        catch (Exception ignored) { return def; }
    }

    private static long toLong(Object v, long def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } 
        catch (Exception ignored) { return def; }
    }
}
