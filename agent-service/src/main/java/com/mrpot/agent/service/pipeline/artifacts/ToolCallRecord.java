package com.mrpot.agent.service.pipeline.artifacts;

import java.util.Map;

/**
 * Record of a tool call made during deep reasoning.
 * Placeholder for Sprint 3 tool integration.
 * 
 * @param toolName    the name of the tool that was called
 * @param arguments   the arguments passed to the tool
 * @param result      the result returned by the tool
 * @param timestampMs timestamp when the tool call completed
 */
public record ToolCallRecord(
    String toolName,
    Map<String, Object> arguments,
    String result,
    long timestampMs
) {
    /**
     * Create a tool call record with current timestamp.
     */
    public static ToolCallRecord of(String toolName, Map<String, Object> arguments, String result) {
        return new ToolCallRecord(toolName, arguments, result, System.currentTimeMillis());
    }
}
