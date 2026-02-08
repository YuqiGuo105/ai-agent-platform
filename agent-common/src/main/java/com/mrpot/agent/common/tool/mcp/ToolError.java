package com.mrpot.agent.common.tool.mcp;

public record ToolError(
    String code,
    String message,
    boolean retryable
) {
  public static final String NOT_FOUND = "NOT_FOUND";
  public static final String BAD_ARGS = "BAD_ARGS";
  public static final String TIMEOUT = "TIMEOUT";
  public static final String INTERNAL = "INTERNAL";
}
