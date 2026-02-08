package com.mrpot.agent.common.tool.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CallToolResponse(
    boolean ok,
    String toolName,
    JsonNode result,
    Boolean cacheHit,
    Long ttlHintSeconds,
    Instant sourceTs,
    ToolError error
) {}
