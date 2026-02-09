package com.mrpot.agent.common.tool.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Response from MCP tool execution")
public record CallToolResponse(
    @Schema(description = "Whether execution succeeded", example = "true", required = true)
    boolean ok,
    
    @Schema(description = "Tool name that was executed", example = "system.ping", required = true)
    String toolName,
    
    @Schema(description = "Execution result (only if ok=true)")
    JsonNode result,
    
    @Schema(description = "Whether result came from cache", example = "false")
    Boolean cacheHit,
    
    @Schema(description = "TTL hint for caching result in seconds", example = "300")
    Long ttlHintSeconds,
    
    @Schema(description = "Source timestamp", example = "2026-02-08T10:00:00Z")
    Instant sourceTs,
    
    @Schema(description = "Error details (only if ok=false)")
    ToolError error
) {}
