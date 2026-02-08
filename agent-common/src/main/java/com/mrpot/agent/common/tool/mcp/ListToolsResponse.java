package com.mrpot.agent.common.tool.mcp;

import java.time.Instant;
import java.util.List;

public record ListToolsResponse(
    List<ToolDefinition> tools,
    Long ttlSeconds,
    Instant sourceTs
) {}
