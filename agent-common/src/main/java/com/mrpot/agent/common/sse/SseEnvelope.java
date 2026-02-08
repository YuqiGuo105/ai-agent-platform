package com.mrpot.agent.common.sse;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SseEnvelope(
    String stage,
    String message,
    Object payload,
    Long seq,
    Long ts,
    String traceId,
    String sessionId
) {}
