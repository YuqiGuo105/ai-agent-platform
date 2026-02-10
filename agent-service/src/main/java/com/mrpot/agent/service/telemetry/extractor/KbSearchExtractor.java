package com.mrpot.agent.service.telemetry.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Extractor for kb.search tool - extracts query terms and result counts.
 */
@Component
public class KbSearchExtractor implements ToolKeyInfoExtractor {
    
    private static final String TOOL_NAME = "kb.search";
    
    @Override
    public boolean supports(String toolName) {
        return TOOL_NAME.equalsIgnoreCase(toolName);
    }
    
    @Override
    public Map<String, Object> extractFromArgs(String toolName, JsonNode args) {
        Map<String, Object> info = new HashMap<>();
        
        if (args == null) {
            return info;
        }
        
        // Extract query text
        if (args.has("query")) {
            String query = args.get("query").asText();
            info.put("queryLength", query.length());
            // Extract first few words as terms
            String[] words = query.split("\\s+");
            if (words.length > 0) {
                info.put("queryTermCount", words.length);
                info.put("queryPreview", truncate(query, 100));
            }
        }
        
        // Extract scope/filter information
        if (args.has("scopeMode")) {
            info.put("scopeMode", args.get("scopeMode").asText());
        }
        
        if (args.has("limit")) {
            info.put("requestedLimit", args.get("limit").asInt());
        }
        
        if (args.has("filters")) {
            info.put("hasFilters", true);
        }
        
        return info;
    }
    
    @Override
    public Map<String, Object> extractFromResult(String toolName, JsonNode result) {
        Map<String, Object> info = new HashMap<>();
        
        if (result == null) {
            return info;
        }
        
        // Extract hit count
        if (result.has("hits") && result.get("hits").isArray()) {
            int hitCount = result.get("hits").size();
            info.put("hitCount", hitCount);
            
            // Extract document IDs
            StringBuilder docIds = new StringBuilder();
            for (int i = 0; i < Math.min(hitCount, 10); i++) {
                JsonNode hit = result.get("hits").get(i);
                if (hit.has("id")) {
                    if (docIds.length() > 0) docIds.append(",");
                    docIds.append(hit.get("id").asText());
                }
            }
            if (docIds.length() > 0) {
                info.put("docIds", docIds.toString());
            }
            
            // Extract top score
            if (hitCount > 0 && result.get("hits").get(0).has("score")) {
                info.put("topScore", result.get("hits").get(0).get("score").asDouble());
            }
        }
        
        // Extract total count if different from hits
        if (result.has("total")) {
            info.put("totalMatches", result.get("total").asInt());
        }
        
        // Extract latency if present
        if (result.has("latencyMs")) {
            info.put("searchLatencyMs", result.get("latencyMs").asLong());
        }
        
        return info;
    }
    
    private String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
}
