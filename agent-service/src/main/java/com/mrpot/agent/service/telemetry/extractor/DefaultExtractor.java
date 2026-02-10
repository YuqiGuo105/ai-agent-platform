package com.mrpot.agent.service.telemetry.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Default extractor for tools without specific extractors.
 * Only stores type and size information for privacy.
 */
@Component
@Order(Integer.MAX_VALUE) // Lowest priority - used as fallback
public class DefaultExtractor implements ToolKeyInfoExtractor {
    
    @Override
    public boolean supports(String toolName) {
        // Always returns true as fallback
        return true;
    }
    
    @Override
    public Map<String, Object> extractFromArgs(String toolName, JsonNode args) {
        Map<String, Object> info = new HashMap<>();
        
        if (args == null) {
            info.put("argsType", "null");
            return info;
        }
        
        info.put("argsType", getNodeType(args));
        
        if (args.isObject()) {
            info.put("argsFieldCount", args.size());
        } else if (args.isArray()) {
            info.put("argsElementCount", args.size());
        }
        
        return info;
    }
    
    @Override
    public Map<String, Object> extractFromResult(String toolName, JsonNode result) {
        Map<String, Object> info = new HashMap<>();
        
        if (result == null) {
            info.put("resultType", "null");
            return info;
        }
        
        info.put("resultType", getNodeType(result));
        
        if (result.isObject()) {
            info.put("resultFieldCount", result.size());
        } else if (result.isArray()) {
            info.put("resultElementCount", result.size());
        } else if (result.isTextual()) {
            info.put("resultTextLength", result.asText().length());
        }
        
        // Extract success/error patterns
        if (result.has("success")) {
            info.put("success", result.get("success").asBoolean());
        }
        if (result.has("ok")) {
            info.put("success", result.get("ok").asBoolean());
        }
        if (result.has("error")) {
            info.put("hasError", true);
        }
        
        return info;
    }
    
    private String getNodeType(JsonNode node) {
        if (node.isObject()) return "object";
        if (node.isArray()) return "array";
        if (node.isTextual()) return "string";
        if (node.isNumber()) return "number";
        if (node.isBoolean()) return "boolean";
        if (node.isNull()) return "null";
        return "unknown";
    }
}
