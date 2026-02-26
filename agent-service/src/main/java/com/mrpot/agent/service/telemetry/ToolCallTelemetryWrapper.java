package com.mrpot.agent.service.telemetry;

import com.fasterxml.jackson.databind.JsonNode;
import com.mrpot.agent.common.telemetry.RunContext;
import com.mrpot.agent.common.telemetry.ToolTelemetryData;
import com.mrpot.agent.common.telemetry.ToolTelemetryEvent;
import com.mrpot.agent.common.tool.mcp.CallToolRequest;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.service.telemetry.extractor.DataRedactor;
import com.mrpot.agent.service.telemetry.extractor.ExtractorRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Wrapper that adds telemetry to tool calls.
 * 
 * Emits three event types:
 * - tool.start: before tool execution (args digest/preview)
 * - tool.end: after successful execution (result digest/preview, duration, cache info)
 * - tool.error: on execution failure (error details)
 * 
 * CRITICAL: Telemetry must NEVER block or fail the main tool call.
 */
@Component
@RequiredArgsConstructor
public class ToolCallTelemetryWrapper {

    private static final Logger log = LoggerFactory.getLogger(ToolCallTelemetryWrapper.class);
    
    private final ToolTelemetryPublisher publisher;
    private final DataRedactor redactor;
    private final ExtractorRegistry extractorRegistry;

    /**
     * Wrap a tool call with telemetry.
     * 
     * @param request the tool call request
     * @param runContext the run context for tracing (may be null)
     * @param toolCallFn the actual tool call function
     * @return the tool call response with telemetry emitted
     */
    public Mono<CallToolResponse> wrapCall(
            CallToolRequest request,
            RunContext runContext,
            Function<CallToolRequest, Mono<CallToolResponse>> toolCallFn) {
        
        // If no context provided, create a minimal one from request fields
        RunContext ctx = runContext != null ? runContext : RunContext.of(
            null,
            request.traceId(),
            request.sessionId(),
            null,
            request.scopeMode()
        );
        
        String toolCallId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();
        int attempt = 1;
        
        // Pre-compute args info
        String argsDigest = redactor.calculateDigest(request.args());
        String argsPreview = redactor.createPreview(request.args());
        int argsSize = redactor.getSize(request.args());
        Map<String, Object> argsKeyInfo = extractorRegistry.extractFromArgs(
            request.toolName(), request.args());
        
        // Emit tool.start event
        emitStartEvent(toolCallId, ctx, request.toolName(), attempt,
            argsDigest, argsPreview, argsSize, argsKeyInfo);
        
        return toolCallFn.apply(request)
            .doOnSuccess(response -> {
                long duration = System.currentTimeMillis() - startTime;
                if (response.ok()) {
                    // Emit tool.end event
                    emitEndEvent(toolCallId, ctx, request.toolName(), attempt,
                        response, duration, argsKeyInfo);
                } else {
                    // Emit tool.error event for logical errors
                    emitErrorEvent(toolCallId, ctx, request.toolName(), attempt,
                        response.error() != null ? response.error().code() : "UNKNOWN",
                        response.error() != null ? response.error().message() : "Unknown error",
                        response.error() != null && response.error().retryable(),
                        duration, argsKeyInfo);
                }
            })
            .doOnError(error -> {
                long duration = System.currentTimeMillis() - startTime;
                // Emit tool.error event for exceptions
                emitErrorEvent(toolCallId, ctx, request.toolName(), attempt,
                    "EXCEPTION",
                    error.getMessage(),
                    isRetryable(error),
                    duration, argsKeyInfo);
            });
    }

    /**
     * Wrap a tool call with telemetry (simplified signature using request fields).
     */
    public Mono<CallToolResponse> wrapCall(
            CallToolRequest request,
            String runId,
            Function<CallToolRequest, Mono<CallToolResponse>> toolCallFn) {
        
        RunContext ctx = RunContext.of(
            runId,
            request.traceId(),
            request.sessionId(),
            null, // userId would come from context
            request.scopeMode()
        );
        return wrapCall(request, ctx, toolCallFn);
    }

    private void emitStartEvent(
            String toolCallId,
            RunContext ctx,
            String toolName,
            int attempt,
            String argsDigest,
            String argsPreview,
            int argsSize,
            Map<String, Object> keyInfo) {
        
        try {
            ToolTelemetryData data = ToolTelemetryData.builder()
                .toolName(toolName)
                .attempt(attempt)
                .argsDigest(argsDigest)
                .argsPreview(argsPreview)
                .argsSize(argsSize)
                .keyInfo(keyInfo)
                .build();
            
            ToolTelemetryEvent event = new ToolTelemetryEvent(
                "1",
                ToolTelemetryEvent.TYPE_START,
                toolCallId,
                ctx.runId(),
                ctx.traceId(),
                ctx.sessionId(),
                ctx.userId(),
                Instant.now(),
                data
            );
            
            publisher.publishAsync(event);
        } catch (Exception e) {
            log.warn("Failed to emit tool.start telemetry: {}", e.getMessage());
        }
    }

    private void emitEndEvent(
            String toolCallId,
            RunContext ctx,
            String toolName,
            int attempt,
            CallToolResponse response,
            long durationMs,
            Map<String, Object> argsKeyInfo) {
        
        try {
            JsonNode result = response.result();
            String resultDigest = redactor.calculateDigest(result);
            String resultPreview = redactor.createPreview(result);
            int resultSize = redactor.getSize(result);
            
            // Extract key info from result
            Map<String, Object> resultKeyInfo = extractorRegistry.extractFromResult(
                toolName, result);
            
            // Merge args and result key info
            Map<String, Object> mergedKeyInfo = new HashMap<>();
            if (argsKeyInfo != null) mergedKeyInfo.putAll(argsKeyInfo);
            if (resultKeyInfo != null) mergedKeyInfo.putAll(resultKeyInfo);
            
            // Determine freshness
            String freshness = determineFreshness(response.cacheHit(), response.ttlHintSeconds());
            
            ToolTelemetryData data = ToolTelemetryData.builder()
                .toolName(toolName)
                .attempt(attempt)
                .resultDigest(resultDigest)
                .resultPreview(resultPreview)
                .resultSize(resultSize)
                .durationMs(durationMs)
                .cacheHit(response.cacheHit())
                .ttlHintSeconds(response.ttlHintSeconds())
                .freshness(freshness)
                .keyInfo(mergedKeyInfo)
                .build();
            
            ToolTelemetryEvent event = new ToolTelemetryEvent(
                "1",
                ToolTelemetryEvent.TYPE_END,
                toolCallId,
                ctx.runId(),
                ctx.traceId(),
                ctx.sessionId(),
                ctx.userId(),
                Instant.now(),
                data
            );
            
            publisher.publishAsync(event);
        } catch (Exception e) {
            log.warn("Failed to emit tool.end telemetry: {}", e.getMessage());
        }
    }

    private void emitErrorEvent(
            String toolCallId,
            RunContext ctx,
            String toolName,
            int attempt,
            String errorCode,
            String errorMessage,
            boolean retryable,
            long durationMs,
            Map<String, Object> keyInfo) {
        
        try {
            // Sanitize error message
            String sanitizedMessage = errorMessage != null 
                ? (errorMessage.length() > 500 ? errorMessage.substring(0, 500) : errorMessage)
                : "Unknown error";
            
            ToolTelemetryData data = ToolTelemetryData.builder()
                .toolName(toolName)
                .attempt(attempt)
                .durationMs(durationMs)
                .errorCode(errorCode)
                .errorMessage(sanitizedMessage)
                .retryable(retryable)
                .keyInfo(keyInfo)
                .build();
            
            ToolTelemetryEvent event = new ToolTelemetryEvent(
                "1",
                ToolTelemetryEvent.TYPE_ERROR,
                toolCallId,
                ctx.runId(),
                ctx.traceId(),
                ctx.sessionId(),
                ctx.userId(),
                Instant.now(),
                data
            );
            
            publisher.publishAsync(event);
        } catch (Exception e) {
            log.warn("Failed to emit tool.error telemetry: {}", e.getMessage());
        }
    }

    private String determineFreshness(Boolean cacheHit, Long ttlHintSeconds) {
        if (cacheHit == null || !cacheHit) {
            return "MISS";
        }
        if (ttlHintSeconds != null && ttlHintSeconds > 0) {
            return "FRESH";
        }
        return "STALE";
    }

    private boolean isRetryable(Throwable error) {
        if (error instanceof java.util.concurrent.TimeoutException) {
            return true;
        }
        if (error instanceof java.io.IOException) {
            return true;
        }
        return false;
    }
}
