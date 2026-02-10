package com.mrpot.agent.common.telemetry;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * Envelope for tool call telemetry events.
 * Sent via RabbitMQ from agent-service to agent-telemetry-service.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolTelemetryEvent(
    String v,              // version, e.g. "1"
    String type,           // "tool.start" | "tool.end" | "tool.error"
    String toolCallId,     // unique ID for this tool call (UUID)
    String runId,          // parent run ID
    String traceId,        // distributed tracing ID
    String sessionId,      // user session ID
    String userId,         // user ID
    Instant ts,            // timestamp
    ToolTelemetryData data // tool-specific payload
) {
    public static final String TYPE_START = "tool.start";
    public static final String TYPE_END = "tool.end";
    public static final String TYPE_ERROR = "tool.error";
}
