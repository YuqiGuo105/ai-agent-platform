package com.mrpot.agent.common.sse;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorEnvelope(
    ErrorCode code,
    String message,
    boolean retryable,
    String detail
) {}
