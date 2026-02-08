package com.mrpot.agent.common.tool.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolError(
    String code,
    String message,
    Boolean retryable
) {}
