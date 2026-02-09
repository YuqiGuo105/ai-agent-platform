package com.mrpot.agent.common.tool.mcp;

import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.api.ToolProfile;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to list available MCP tools")
public record ListToolsRequest(
    @Schema(description = "Scope mode for filtering tools", example = "AUTO")
    ScopeMode scopeMode,
    
    @Schema(description = "Tool profile filter (BASIC, DEFAULT, FULL)", example = "DEFAULT")
    ToolProfile toolProfile,
    
    @Schema(description = "Trace ID for distributed tracing", example = "trace-abc123")
    String traceId,
    
    @Schema(description = "Session ID", example = "sess-12345")
    String sessionId
) {}
