package com.mrpot.agent.common.tool.mcp;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "MCP tool execution error details")
public record ToolError(
    @Schema(description = "Error code (NOT_FOUND, BAD_ARGS, TIMEOUT, INTERNAL)", example = "NOT_FOUND", required = true)
    String code,
    
    @Schema(description = "Human-readable error message", example = "Tool not found", required = true)
    String message,
    
    @Schema(description = "Whether the operation can be retried", example = "false", required = true)
    boolean retryable
) {
  public static final String NOT_FOUND = "NOT_FOUND";
  public static final String BAD_ARGS = "BAD_ARGS";
  public static final String TIMEOUT = "TIMEOUT";
  public static final String INTERNAL = "INTERNAL";
}
