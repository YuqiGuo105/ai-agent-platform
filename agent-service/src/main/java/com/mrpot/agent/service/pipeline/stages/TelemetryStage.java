package com.mrpot.agent.service.pipeline.stages;

import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.telemetry.RunLogEnvelope;
import com.mrpot.agent.service.pipeline.PipelineContext;
import com.mrpot.agent.service.pipeline.Processor;
import com.mrpot.agent.service.telemetry.RunLogPublisher;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Telemetry stage - publishes run log events to message queue.
 * 
 * This stage publishes telemetry events at key points during pipeline execution, including:
 * - run.start: when pipeline starts
 * - run.final: when pipeline completes
 * - run.failed: when pipeline fails (triggered by PipelineRunner error handling)
 * 
 * Telemetry events are sent to telemetry service via RabbitMQ for storage and analysis.
 * 
 * Features:
 * - Publishing failures will not cause pipeline failure (never affects main answer flow)
 * - Supports different event types (start/final)
 * - Silent execution, does not emit SSE events
 */
@Slf4j
public class TelemetryStage implements Processor<Void, SseEnvelope> {
    
    /**
     * Telemetry event type
     */
    public enum EventType {
        START("run.start"),
        FINAL("run.final"),
        FAILED("run.failed");
        
        private final String value;
        
        EventType(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }
    
    private static final String VERSION = "1";
    private static final String DEFAULT_MODEL = "deepseek";
    private static final int MAX_QUESTION_LENGTH = 3800;
    private static final int MAX_ANSWER_LENGTH = 11000;
    
    private final RunLogPublisher publisher;
    private final EventType eventType;
    
    public TelemetryStage(RunLogPublisher publisher, EventType eventType) {
        this.publisher = publisher;
        this.eventType = eventType;
    }
    
    /**
     * Create START event stage
     */
    public static TelemetryStage start(RunLogPublisher publisher) {
        return new TelemetryStage(publisher, EventType.START);
    }
    
    /**
     * Create FINAL event stage
     */
    public static TelemetryStage finalEvent(RunLogPublisher publisher) {
        return new TelemetryStage(publisher, EventType.FINAL);
    }
    
    @Override
    public Mono<SseEnvelope> process(Void input, PipelineContext context) {
        return Mono.fromRunnable(() -> publishEvent(context))
            .then(Mono.empty());
    }
    
    /**
     * Publish telemetry event
     */
    private void publishEvent(PipelineContext context) {
        try {
            RunLogEnvelope envelope = createEnvelope(context);
            publisher.publish(envelope);
            
            log.debug("Published telemetry event: type={}, runId={}", 
                eventType.getValue(), context.runId());
        } catch (Exception e) {
            // Never affects main answer flow
            log.warn("Failed to publish telemetry event: type={}, runId={}, error={}",
                eventType.getValue(), context.runId(), e.getMessage());
        }
    }
    
    /**
     * Create telemetry event envelope
     */
    private RunLogEnvelope createEnvelope(PipelineContext context) {
        Map<String, Object> data = switch (eventType) {
            case START -> createStartData(context);
            case FINAL -> createFinalData(context);
            case FAILED -> createFailedData(context);
        };
        
        return new RunLogEnvelope(
            VERSION,
            eventType.getValue(),
            context.runId(),
            context.traceId(),
            context.sessionId(),
            context.userId(),
            context.scopeMode().name(),
            DEFAULT_MODEL,
            Instant.now(),
            data
        );
    }
    
    /**
     * Create START event data.
     * Includes complexity score, execution mode, and feature breakdown.
     */
    private Map<String, Object> createStartData(PipelineContext context) {
        String question = context.request().question();
        Map<String, Object> data = new HashMap<>();
        
        // Basic fields
        data.put("question", truncate(question, MAX_QUESTION_LENGTH));
        data.put("executionMode", context.executionMode());
        
        // Replay fields (Sprint 7)
        if (context.getParentRunId() != null) {
            data.put("parentRunId", context.getParentRunId());
        }
        if (context.getReplayMode() != null) {
            data.put("replayMode", context.getReplayMode().name());
        }
        
        // Complexity scoring fields (Sprint 6)
        double complexityScore = context.getComplexityScore();
        if (complexityScore > 0) {
            data.put("complexityScore", complexityScore);
        }
        
        Map<String, Double> breakdown = context.getFeatureBreakdown();
        if (!breakdown.isEmpty()) {
            data.put("featureBreakdown", breakdown);
        }
        
        return data;
    }
    
    /**
     * Create FINAL event data.
     * Includes latency, deep rounds used, tool call metrics.
     */
    private Map<String, Object> createFinalData(PipelineContext context) {
        String answer = context.getFinalAnswer();
        long totalLatencyMs = context.elapsedMs();
        
        Map<String, Object> data = new HashMap<>();
        
        // Basic fields
        data.put("answerFinal", truncate(answer, MAX_ANSWER_LENGTH));
        data.put("totalLatencyMs", totalLatencyMs);
        data.put("executionMode", context.executionMode());
        
        // Replay fields (Sprint 7)
        if (context.getParentRunId() != null) {
            data.put("parentRunId", context.getParentRunId());
        }
        if (context.getReplayMode() != null) {
            data.put("replayMode", context.getReplayMode().name());
        }
        
        // Deep mode metrics (Sprint 6)
        if ("DEEP".equals(context.executionMode())) {
            data.put("deepRoundsUsed", context.getDeepRoundsUsed());
        }
        
        // Tool call metrics (Sprint 6)
        int toolCallsCount = context.getToolCallsCount();
        if (toolCallsCount > 0) {
            data.put("toolCallsCount", toolCallsCount);
            data.put("toolSuccessRate", context.getToolSuccessRate());
        }
        
        // Complexity score (for correlation analysis)
        double complexityScore = context.getComplexityScore();
        if (complexityScore > 0) {
            data.put("complexityScore", complexityScore);
        }
        
        return data;
    }
    
    /**
     * Create FAILED event data
     */
    private Map<String, Object> createFailedData(PipelineContext context) {
        String errorCode = context.getOrDefault("errorCode", "Unknown");
        return Map.of(
            "errorCode", errorCode
        );
    }
    
    /**
     * Truncate string
     */
    private String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + " ...[truncated]";
    }
}
