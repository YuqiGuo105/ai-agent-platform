package com.mrpot.agent.common.tool.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record CallToolResponse(
    boolean ok,
    String toolName,
    JsonNode result,
    Boolean cacheHit,
    Long ttlHintSeconds,
    Instant sourceTs,
    ToolError error
) {}
