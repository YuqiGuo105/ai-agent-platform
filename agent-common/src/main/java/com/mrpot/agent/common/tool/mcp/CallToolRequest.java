package com.mrpot.agent.common.tool.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.api.ToolProfile;

public record CallToolRequest(
    String toolName,
    JsonNode args,
    ScopeMode scopeMode,
    ToolProfile toolProfile,
    String traceId,
    String sessionId
) {}
