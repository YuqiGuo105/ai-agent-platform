package com.mrpot.agent.service.telemetry.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Registry for tool key info extractors.
 * Selects the appropriate extractor based on tool name.
 */
@Component
public class ExtractorRegistry {
    
    private final List<ToolKeyInfoExtractor> extractors;
    private final DefaultExtractor defaultExtractor;
    
    public ExtractorRegistry(List<ToolKeyInfoExtractor> extractors, DefaultExtractor defaultExtractor) {
        // Filter out the default extractor from the list
        this.extractors = extractors.stream()
            .filter(e -> !(e instanceof DefaultExtractor))
            .toList();
        this.defaultExtractor = defaultExtractor;
    }
    
    /**
     * Get the appropriate extractor for the given tool.
     */
    public ToolKeyInfoExtractor getExtractor(String toolName) {
        return extractors.stream()
            .filter(e -> e.supports(toolName))
            .findFirst()
            .orElse(defaultExtractor);
    }
    
    /**
     * Extract key info from tool arguments.
     */
    public Map<String, Object> extractFromArgs(String toolName, JsonNode args) {
        return getExtractor(toolName).extractFromArgs(toolName, args);
    }
    
    /**
     * Extract key info from tool result.
     */
    public Map<String, Object> extractFromResult(String toolName, JsonNode result) {
        return getExtractor(toolName).extractFromResult(toolName, result);
    }
}
