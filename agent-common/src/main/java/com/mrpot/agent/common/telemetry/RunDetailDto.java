package com.mrpot.agent.common.telemetry;

import java.util.List;

/**
 * DTO for representing detailed run information including tool calls.
 * Used by the trace query API to return complete run details.
 */
public record RunDetailDto(
    String runId,
    String traceId,
    String sessionId,
    String mode,
    String model,
    String status,
    Long totalLatencyMs,
    Integer kbHitCount,
    List<ToolCallDto> tools
) {}
