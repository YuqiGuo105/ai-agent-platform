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
import java.util.ArrayList;
import java.util.List;

/**
 * Planning next step tool.
 * Determines the next action based on current state and available tools.
 */
@Slf4j
@Component
public class PlanningNextStepTools implements ToolHandler {
    
    private static final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() {
        return "planning.next_step";
    }
    
    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
            name(),
            "Determine the next action (call_tool, skip, or proceed) based on current state",
            "1.0.0",
            DeepToolSchemas.planningNextStepInput(),
            DeepToolSchemas.planningNextStepOutput(),
            null,
            30L
        );
    }
    
    @Override
    public CallToolResponse handle(JsonNode args, ToolContext ctx) {
        log.debug("Planning next_step called with args: {}", args);
        
        // Validate input
        DeepToolInputValidator.ValidationResult validation = 
            DeepToolInputValidator.validatePlanningNextStep(args);
        
        if (!validation.valid()) {
            return createErrorResponse("Validation failed: " + String.join(", ", validation.errors()));
        }
        
        try {
            String currentState = args.get("currentState").asText();
            List<String> availableTools = new ArrayList<>();
            
            if (args.has("availableTools") && args.get("availableTools").isArray()) {
                for (JsonNode tool : args.get("availableTools")) {
                    availableTools.add(tool.asText());
                }
            }
            
            // Determine next action
            NextStepDecision decision = determineNextStep(currentState, availableTools);
            
            ObjectNode result = mapper.createObjectNode();
            result.put("action", decision.action);
            result.put("toolName", decision.toolName != null ? decision.toolName : "");
            result.put("reasoning", decision.reasoning);
            
            log.info("Next step determined: action={}, tool={}", 
                decision.action, decision.toolName);
            
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
            log.error("Planning next_step failed: {}", e.getMessage(), e);
            return createErrorResponse(e.getMessage());
        }
    }
    
    private NextStepDecision determineNextStep(String currentState, List<String> availableTools) {
        String stateLower = currentState.toLowerCase();
        
        // Check if we need analytical tools
        if (stateLower.contains("need to analyze") || stateLower.contains("analyze data")) {
            if (availableTools.contains("reasoning.analyze")) {
                return new NextStepDecision(
                    "call_tool",
                    "reasoning.analyze",
                    "Current state indicates analysis needed, reasoning.analyze tool available"
                );
            }
        }
        
        // Check if we need comparison
        if (stateLower.contains("need to compare") || stateLower.contains("compare options")) {
            if (availableTools.contains("reasoning.compare")) {
                return new NextStepDecision(
                    "call_tool",
                    "reasoning.compare",
                    "Current state indicates comparison needed, reasoning.compare tool available"
                );
            }
        }
        
        // Check if we need to store information
        if (stateLower.contains("remember") || stateLower.contains("store for later")) {
            if (availableTools.contains("memory.store")) {
                return new NextStepDecision(
                    "call_tool",
                    "memory.store",
                    "Current state indicates memory storage needed"
                );
            }
        }
        
        // Check if we need to recall information
        if (stateLower.contains("recall") || stateLower.contains("retrieve from memory")) {
            if (availableTools.contains("memory.recall")) {
                return new NextStepDecision(
                    "call_tool",
                    "memory.recall",
                    "Current state indicates memory recall needed"
                );
            }
        }
        
        // Check if state is complete
        if (stateLower.contains("complete") || stateLower.contains("done") || stateLower.contains("finished")) {
            return new NextStepDecision(
                "proceed",
                null,
                "Current state indicates completion, proceeding to next phase"
            );
        }
        
        // Default: skip if no clear action
        return new NextStepDecision(
            "skip",
            null,
            "No clear tool need identified in current state, skipping tool call"
        );
    }
    
    private CallToolResponse createErrorResponse(String message) {
        return new CallToolResponse(
            false,
            name(),
            null,
            false,
            null,
            Instant.now(),
            new ToolError("NEXT_STEP_ERROR", message, false)
        );
    }
    
    private record NextStepDecision(String action, String toolName, String reasoning) {}
}
