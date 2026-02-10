package com.mrpot.agent.service.telemetry.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Extractor for redis.get/set tools - extracts key patterns and value sizes.
 */
@Component
public class RedisExtractor implements ToolKeyInfoExtractor {
    
    @Override
    public boolean supports(String toolName) {
        if (toolName == null) return false;
        String lower = toolName.toLowerCase();
        return lower.startsWith("redis.") 
            || lower.startsWith("cache.");
    }
    
    @Override
    public Map<String, Object> extractFromArgs(String toolName, JsonNode args) {
        Map<String, Object> info = new HashMap<>();
        
        if (args == null) {
            return info;
        }
        
        // Extract operation type
        info.put("operation", extractOperation(toolName));
        
        // Extract key info (without the actual key for privacy)
        if (args.has("key")) {
            String key = args.get("key").asText();
            info.put("keyLength", key.length());
            info.put("keyPrefix", extractKeyPrefix(key));
        }
        
        // For multi-key operations
        if (args.has("keys") && args.get("keys").isArray()) {
            info.put("keyCount", args.get("keys").size());
        }
        
        // Extract TTL for set operations
        if (args.has("ttl")) {
            info.put("ttlSeconds", args.get("ttl").asLong());
        }
        
        if (args.has("expireSeconds")) {
            info.put("ttlSeconds", args.get("expireSeconds").asLong());
        }
        
        // Extract value size for set operations
        if (args.has("value")) {
            JsonNode value = args.get("value");
            if (value.isTextual()) {
                info.put("valueSize", value.asText().length());
            } else {
                info.put("valueSize", value.toString().length());
            }
        }
        
        return info;
    }
    
    @Override
    public Map<String, Object> extractFromResult(String toolName, JsonNode result) {
        Map<String, Object> info = new HashMap<>();
        
        if (result == null) {
            return info;
        }
        
        // Extract hit/miss for get operations
        if (result.has("found")) {
            info.put("cacheHit", result.get("found").asBoolean());
        }
        
        if (result.has("exists")) {
            info.put("cacheHit", result.get("exists").asBoolean());
        }
        
        // Extract value size from result
        if (result.has("value")) {
            JsonNode value = result.get("value");
            if (value.isNull()) {
                info.put("cacheHit", false);
            } else {
                info.put("cacheHit", true);
                if (value.isTextual()) {
                    info.put("valueSize", value.asText().length());
                } else {
                    info.put("valueSize", value.toString().length());
                }
            }
        }
        
        // Extract success status
        if (result.has("success")) {
            info.put("operationSuccess", result.get("success").asBoolean());
        }
        
        // Extract TTL remaining
        if (result.has("ttlRemaining")) {
            info.put("ttlRemaining", result.get("ttlRemaining").asLong());
        }
        
        return info;
    }
    
    private String extractOperation(String toolName) {
        if (toolName == null) return "unknown";
        String[] parts = toolName.split("\\.");
        if (parts.length >= 2) {
            return parts[1].toLowerCase();
        }
        return toolName;
    }
    
    private String extractKeyPrefix(String key) {
        if (key == null) return null;
        
        // Extract prefix before first : or _
        int colonIdx = key.indexOf(':');
        int underscoreIdx = key.indexOf('_');
        
        int prefixEnd = -1;
        if (colonIdx > 0 && underscoreIdx > 0) {
            prefixEnd = Math.min(colonIdx, underscoreIdx);
        } else if (colonIdx > 0) {
            prefixEnd = colonIdx;
        } else if (underscoreIdx > 0) {
            prefixEnd = underscoreIdx;
        }
        
        if (prefixEnd > 0 && prefixEnd <= 32) {
            return key.substring(0, prefixEnd);
        }
        
        // Limit to reasonable length
        return key.length() > 32 ? key.substring(0, 32) : key;
    }
}
