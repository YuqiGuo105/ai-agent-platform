package com.mrpot.agent.service.telemetry.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * Interface for tool-specific key information extraction.
 * Implementations extract business-relevant data from tool responses
 * for analytics without storing full payloads.
 */
public interface ToolKeyInfoExtractor {
    
    /**
     * Check if this extractor can handle the given tool.
     */
    boolean supports(String toolName);
    
    /**
     * Extract key information from tool arguments.
     * 
     * @param toolName the tool name
     * @param args the tool arguments
     * @return extracted key-value pairs (may be empty, never null)
     */
    Map<String, Object> extractFromArgs(String toolName, JsonNode args);
    
    /**
     * Extract key information from tool result.
     * 
     * @param toolName the tool name
     * @param result the tool execution result
     * @return extracted key-value pairs (may be empty, never null)
     */
    Map<String, Object> extractFromResult(String toolName, JsonNode result);
}
