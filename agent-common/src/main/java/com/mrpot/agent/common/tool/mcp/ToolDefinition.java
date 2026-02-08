package com.mrpot.agent.common.tool.mcp;

import com.fasterxml.jackson.databind.JsonNode;

public record ToolDefinition(
    String name,
    String description,
    String version,
    JsonNode inputSchema,
    JsonNode outputSchema,
    RateLimitHint rateLimit,
    Long ttlHintSeconds
) {}
