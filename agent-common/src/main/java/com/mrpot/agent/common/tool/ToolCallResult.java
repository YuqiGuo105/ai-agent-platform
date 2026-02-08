package com.mrpot.agent.common.tool;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolCallResult(
    boolean ok,
    String toolName,
    JsonNode data,
    String error,
    Long sourceTs,
    Boolean cacheHit,
    Long ttlHintSeconds
) {}
