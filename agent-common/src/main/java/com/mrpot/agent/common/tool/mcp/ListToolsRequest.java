package com.mrpot.agent.common.tool.mcp;

import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.api.ToolProfile;

public record ListToolsRequest(
    ScopeMode scopeMode,
    ToolProfile toolProfile,
    String traceId,
    String sessionId
) {}
