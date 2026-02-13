package com.mrpot.agent.tools.tool.deep;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ToolDefinition;
import com.mrpot.agent.common.tool.mcp.ToolError;
import com.mrpot.agent.tools.service.ToolContext;
import com.mrpot.agent.tools.service.ToolHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Planning decomposition tool.
 * Breaks down complex tasks into subtasks with dependencies.
 */
@Slf4j
@Component
public class PlanningDecomposeTools implements ToolHandler {
    
    private static final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() {
        return "planning.decompose";
    }
    
    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
            name(),
            "Decompose a complex objective into manageable subtasks",
            "1.0.0",
            DeepToolSchemas.planningDecomposeInput(),
            DeepToolSchemas.planningDecomposeOutput(),
            null,
            60L
        );
    }
    
    @Override
    public CallToolResponse handle(JsonNode args, ToolContext ctx) {
        log.debug("Planning decompose called with args: {}", args);
        
        // Validate input
        DeepToolInputValidator.ValidationResult validation = 
            DeepToolInputValidator.validatePlanningDecompose(args);
        
        if (!validation.valid()) {
            return createErrorResponse("Validation failed: " + String.join(", ", validation.errors()));
        }
        
        try {
            String objective = args.get("objective").asText();
            String context = args.has("context") ? args.get("context").asText() : "";
            
            // Simple decomposition logic - in production this would use LLM
            List<String> subtasks = decomposeObjective(objective, context);
            List<String> dependencies = identifyDependencies(subtasks);
            
            ObjectNode result = mapper.createObjectNode();
            ArrayNode subtasksArray = mapper.createArrayNode();
            subtasks.forEach(subtasksArray::add);
            result.set("subtasks", subtasksArray);
            
            ArrayNode depsArray = mapper.createArrayNode();
            dependencies.forEach(depsArray::add);
            result.set("dependencies", depsArray);
            
            log.info("Decomposed objective into {} subtasks with {} dependencies", 
                subtasks.size(), dependencies.size());
            
            return new CallToolResponse(
                true,
                name(),
                result,
                false,
                60L,
                Instant.now(),
                null
            );
            
        } catch (Exception e) {
            log.error("Planning decompose failed: {}", e.getMessage(), e);
            return createErrorResponse(e.getMessage());
        }
    }
    
    private List<String> decomposeObjective(String objective, String context) {
        List<String> subtasks = new ArrayList<>();
        
        // Simple heuristic decomposition
        String lowerObjective = objective.toLowerCase();
        
        subtasks.add("Understand the goal: " + truncate(objective, 50));
        
        if (lowerObjective.contains("compare") || lowerObjective.contains("versus")) {
            subtasks.add("Identify items to compare");
            subtasks.add("Define comparison criteria");
            subtasks.add("Evaluate each item");
            subtasks.add("Synthesize comparison results");
        } else if (lowerObjective.contains("analyze") || lowerObjective.contains("examine")) {
            subtasks.add("Gather relevant data");
            subtasks.add("Identify key patterns");
            subtasks.add("Draw conclusions");
        } else if (lowerObjective.contains("how to") || lowerObjective.contains("steps")) {
            subtasks.add("Identify prerequisites");
            subtasks.add("Outline main steps");
            subtasks.add("Add details and considerations");
        } else {
            subtasks.add("Research relevant information");
            subtasks.add("Formulate answer");
            subtasks.add("Verify accuracy");
        }
        
        subtasks.add("Compile final response");
        
        return subtasks;
    }
    
    private List<String> identifyDependencies(List<String> subtasks) {
        List<String> dependencies = new ArrayList<>();
        
        for (int i = 1; i < subtasks.size(); i++) {
            dependencies.add(subtasks.get(i - 1) + " -> " + subtasks.get(i));
        }
        
        return dependencies;
    }
    
    private CallToolResponse createErrorResponse(String message) {
        return new CallToolResponse(
            false,
            name(),
            null,
            false,
            null,
            Instant.now(),
            new ToolError("DECOMPOSE_ERROR", message, false)
        );
    }
    
    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
