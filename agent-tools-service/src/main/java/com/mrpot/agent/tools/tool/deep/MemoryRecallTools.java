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
import java.util.Set;

/**
 * Memory recall tool.
 * Retrieves stored facts or plans from isolated memory lanes.
 * 
 * Key pattern: deep:memory:{lane}:{sessionId}:{key}
 * Supported lanes: facts, plans
 * 
 * Note: Uses shared MemoryStoreTools store. In production, this would
 * read from Redis directly.
 */
@Slf4j
@Component
public class MemoryRecallTools implements ToolHandler {
    
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Set<String> VALID_LANES = Set.of("facts", "plans");
    
    private final MemoryStoreTools memoryStoreTools;
    
    public MemoryRecallTools(MemoryStoreTools memoryStoreTools) {
        this.memoryStoreTools = memoryStoreTools;
    }
    
    @Override
    public String name() {
        return "memory.recall";
    }
    
    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
            name(),
            "Retrieve stored facts or plans from isolated memory lanes",
            "1.0.0",
            DeepToolSchemas.memoryRecallInput(),
            DeepToolSchemas.memoryRecallOutput(),
            null,
            30L
        );
    }
    
    @Override
    public CallToolResponse handle(JsonNode args, ToolContext ctx) {
        log.debug("Memory recall called with args: {}", args);
        
        // Validate input
        DeepToolInputValidator.ValidationResult validation = 
            DeepToolInputValidator.validateMemoryRecall(args);
        
        if (!validation.valid()) {
            return createErrorResponse("Validation failed: " + String.join(", ", validation.errors()));
        }
        
        try {
            String lane = args.get("lane").asText();
            String key = args.get("key").asText();
            
            // Validate lane
            if (!VALID_LANES.contains(lane)) {
                return createErrorResponse("Invalid lane: " + lane + ". Must be one of: " + VALID_LANES);
            }
            
            // Get session ID from context
            String sessionId = ctx != null && ctx.sessionId() != null ? ctx.sessionId() : "default";
            
            // Construct key following pattern: deep:memory:{lane}:{sessionId}:{key}
            String fullKey = memoryStoreTools.buildKey(lane, sessionId, key);
            
            // Retrieve from store
            MemoryStoreTools.StoredEntry entry = memoryStoreTools.getMemoryStore().get(fullKey);
            
            ObjectNode result = mapper.createObjectNode();
            
            if (entry == null) {
                log.debug("Memory recall: key not found: {}", fullKey);
                result.put("found", false);
                result.putNull("value");
            } else if (entry.isExpired()) {
                log.debug("Memory recall: key expired: {}", fullKey);
                // Clean up expired entry
                memoryStoreTools.getMemoryStore().remove(fullKey);
                result.put("found", false);
                result.putNull("value");
            } else {
                log.info("Memory recall: found key={} in lane={}", key, lane);
                result.put("found", true);
                result.put("value", entry.value());
            }
            
            return new CallToolResponse(
                true,
                name(),
                result,
                false,
                30L,
                Instant.now(),
                null
            );
            
        } catch (Exception e) {
            log.error("Memory recall failed: {}", e.getMessage(), e);
            return createErrorResponse(e.getMessage());
        }
    }
    
    private CallToolResponse createErrorResponse(String message) {
        return new CallToolResponse(
            false,
            name(),
            null,
            false,
            null,
            Instant.now(),
            new ToolError("MEMORY_RECALL_ERROR", message, false)
        );
    }
}
