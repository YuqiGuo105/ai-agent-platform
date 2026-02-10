package com.mrpot.agent.common.telemetry;

import java.time.Instant;

/**
 * DTO for representing a single tool call in API responses.
 */
public record ToolCallDto(
    String toolCallId,
    String toolName,
    Integer attempt,
    Boolean ok,
    Long durationMs,
    String argsPreview,
    String resultPreview,
    Boolean cacheHit,
    String freshness,
    String errorCode,
    String errorMsg,
    Boolean retryable,
    Instant sourceTs
) {}
