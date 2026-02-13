package com.mrpot.agent.common.deep;

import java.util.Map;

/**
 * Record of a tool call made during deep reasoning.
 * 
 * @param toolName    the name of the tool that was called
 * @param args        the arguments passed to the tool (as JSON-compatible map)
 * @param result      the result returned by the tool
 * @param latencyMs   latency of the tool call in milliseconds
 * @param success     whether the tool call succeeded
 * @param timestampMs timestamp when the tool call completed
 */
public record ToolCallRecord(
    String toolName,
    Map<String, Object> args,
    String result,
    long latencyMs,
    boolean success,
    long timestampMs
) {
    /**
     * Create a successful tool call record with current timestamp.
     */
    public static ToolCallRecord success(String toolName, Map<String, Object> args, String result, long latencyMs) {
        return new ToolCallRecord(toolName, args, result, latencyMs, true, System.currentTimeMillis());
    }
    
    /**
     * Create a failed tool call record with current timestamp.
     */
    public static ToolCallRecord failure(String toolName, Map<String, Object> args, String error, long latencyMs) {
        return new ToolCallRecord(toolName, args, error, latencyMs, false, System.currentTimeMillis());
    }
}
