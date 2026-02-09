package com.mrpot.agent.common.tool.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.api.ToolProfile;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to execute an MCP tool")
public record CallToolRequest(
    @Schema(description = "Tool name to execute", example = "system.ping", required = true)
    String toolName,
    
    @Schema(description = "Tool arguments as JSON", example = "{}", nullable = true)
    JsonNode args,
    
    @Schema(description = "Scope mode for permission checks", example = "AUTO")
    ScopeMode scopeMode,
    
    @Schema(description = "Tool profile context", example = "DEFAULT")
    ToolProfile toolProfile,
    
    @Schema(description = "Trace ID for distributed tracing", example = "trace-abc123")
    String traceId,
    
    @Schema(description = "Session ID", example = "sess-12345")
    String sessionId
) {}
