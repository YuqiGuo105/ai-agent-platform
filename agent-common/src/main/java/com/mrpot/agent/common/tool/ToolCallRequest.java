package com.mrpot.agent.common.tool;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public record ToolCallRequest(
    String traceId,
    String sessionId,
    String toolName,
    JsonNode arguments,
    Map<String, Object> meta
) {}
