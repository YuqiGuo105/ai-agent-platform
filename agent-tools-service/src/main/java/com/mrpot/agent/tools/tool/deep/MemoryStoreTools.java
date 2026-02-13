package com.mrpot.agent.tools.tool.deep;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ToolDefinition;
import com.mrpot.agent.common.tool.mcp.ToolError;
import com.mrpot.agent.tools.service.ToolContext;
import com.mrpot.agent.tools.service.ToolHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Memory store tool.
 * Stores facts or plans in isolated memory lanes.
 * 
 * Key pattern: deep:memory:{lane}:{sessionId}:{key}
 * Supported lanes: facts, plans
 * 
 * Note: Uses ConcurrentHashMap for simplicity. In production, this would
 * be backed by Redis with proper TTL management.
 */
@Slf4j
@Component
public class MemoryStoreTools implements ToolHandler {
    
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Set<String> VALID_LANES = Set.of("facts", "plans");
    
    // Simple in-memory store (would be Redis in production)
    // Key format: deep:memory:{lane}:{sessionId}:{key}
    private final Map<String, StoredEntry> memoryStore = new ConcurrentHashMap<>();
    
    @Override
    public String name() {
        return "memory.store";
    }
    
    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
            name(),
            "Store facts or plans in isolated memory lanes",
            "1.0.0",
            DeepToolSchemas.memoryStoreInput(),
            DeepToolSchemas.memoryStoreOutput(),
            null,
            30L
        );
    }
    
    @Override
    public CallToolResponse handle(JsonNode args, ToolContext ctx) {
        log.debug("Memory store called with args: {}", args);
        
        // Validate input
        DeepToolInputValidator.ValidationResult validation = 
            DeepToolInputValidator.validateMemoryStore(args);
        
        if (!validation.valid()) {
            return createErrorResponse("Validation failed: " + String.join(", ", validation.errors()));
        }
        
        try {
            String lane = args.get("lane").asText();
            String key = args.get("key").asText();
            String value = args.get("value").asText();
            Long ttlSeconds = args.has("ttlSeconds") ? args.get("ttlSeconds").asLong() : 3600L;
            
            // Validate lane
            if (!VALID_LANES.contains(lane)) {
                return createErrorResponse("Invalid lane: " + lane + ". Must be one of: " + VALID_LANES);
            }
            
            // Get session ID from context
            String sessionId = ctx != null && ctx.sessionId() != null ? ctx.sessionId() : "default";
            
            // Construct key following pattern: deep:memory:{lane}:{sessionId}:{key}
            String fullKey = buildKey(lane, sessionId, key);
            
            // Store with expiry
            long expiryMs = System.currentTimeMillis() + (ttlSeconds * 1000);
            memoryStore.put(fullKey, new StoredEntry(value, expiryMs));
            
            log.info("Stored to memory: lane={}, key={}, ttl={}s", lane, key, ttlSeconds);
            
            ObjectNode result = mapper.createObjectNode();
            result.put("stored", true);
            result.put("fullKey", fullKey);
            result.put("expiresAt", expiryMs);
            
            return new CallToolResponse(
                true,
                name(),
                result,
                false,
                ttlSeconds,
                Instant.now(),
                null
            );
            
        } catch (Exception e) {
            log.error("Memory store failed: {}", e.getMessage(), e);
            return createErrorResponse(e.getMessage());
        }
    }
    
    /**
     * Build the full key following pattern: deep:memory:{lane}:{sessionId}:{key}
     */
    String buildKey(String lane, String sessionId, String key) {
        return String.format("deep:memory:%s:%s:%s", lane, sessionId, key);
    }
    
    /**
     * Exposed for MemoryRecallTools to access the store.
     */
    Map<String, StoredEntry> getMemoryStore() {
        return memoryStore;
    }
    
    private CallToolResponse createErrorResponse(String message) {
        return new CallToolResponse(
            false,
            name(),
            null,
            false,
            null,
            Instant.now(),
            new ToolError("MEMORY_STORE_ERROR", message, false)
        );
    }
    
    /**
     * Entry stored in memory with expiry time.
     */
    record StoredEntry(String value, long expiryMs) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiryMs;
        }
    }
}
