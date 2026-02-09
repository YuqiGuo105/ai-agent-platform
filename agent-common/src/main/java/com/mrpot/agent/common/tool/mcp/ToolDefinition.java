package com.mrpot.agent.common.tool.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "MCP tool definition with schema and rate limits")
public record ToolDefinition(
    @Schema(description = "Tool name", example = "system.ping", required = true)
    String name,
    
    @Schema(description = "Tool description", example = "Health check tool")
    String description,
    
    @Schema(description = "Tool version", example = "1.0.0")
    String version,
    
    @Schema(description = "JSON schema for input validation")
    JsonNode inputSchema,
    
    @Schema(description = "JSON schema for output structure")
    JsonNode outputSchema,
    
    @Schema(description = "Rate limit hint")
    RateLimitHint rateLimit,
    
    @Schema(description = "TTL hint in seconds for caching", example = "300")
    Long ttlHintSeconds
) {}
