package com.mrpot.agent.common.tool.mcp;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Response with list of available MCP tools")
public record ListToolsResponse(
    @Schema(description = "List of tool definitions with schemas", required = true)
    List<ToolDefinition> tools,
    
    @Schema(description = "Cache TTL in seconds", example = "300")
    Long ttlSeconds,
    
    @Schema(description = "Source timestamp", example = "2026-02-08T10:00:00Z")
    Instant sourceTs
) {}
