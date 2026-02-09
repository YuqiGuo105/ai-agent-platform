package com.mrpot.agent.common.sse;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Server-Sent Event envelope for streaming responses")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SseEnvelope(
    @Schema(description = "Event stage (start, answer_delta, tool_call_start, tool_call_result, answer_final)", example = "answer_delta")
    String stage,
    
    @Schema(description = "Human-readable message", example = "Generating answer...")
    String message,
    
    @Schema(description = "Event payload (type varies by stage)")
    Object payload,
    
    @Schema(description = "Event sequence number", example = "5")
    Long seq,
    
    @Schema(description = "Timestamp in milliseconds", example = "1707408000000")
    Long ts,
    
    @Schema(description = "Trace ID for distributed tracing", example = "abc123")
    String traceId,
    
    @Schema(description = "Session ID", example = "sess-12345")
    String sessionId
) {}
