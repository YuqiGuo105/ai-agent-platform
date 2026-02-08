package com.mrpot.agent.common.telemetry;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TelemetryEvent(
    TelemetryEventType type,
    String traceId,
    String sessionId,
    String stage,
    Long ts,
    Long latencyMs,
    Map<String, Object> attrs
) {}
