package com.mrpot.agent.common.tool.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolDefinition(
    String name,
    String description,
    String version,
    JsonNode inputSchema,
    JsonNode outputSchema,
    RateLimitHint rateLimit,
    Long ttlHintSeconds
) {}
