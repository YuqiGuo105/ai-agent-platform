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
 * Reasoning comparison tool.
 * Compares multiple items or hypotheses based on criteria.
 */
@Slf4j
@Component
public class ReasoningCompareTools implements ToolHandler {
    
    private static final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() {
        return "reasoning.compare";
    }
    
    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
            name(),
            "Compare multiple items or hypotheses based on specified criteria",
            "1.0.0",
            DeepToolSchemas.reasoningCompareInput(),
            DeepToolSchemas.reasoningCompareOutput(),
            null,
            60L
        );
    }
    
    @Override
    public CallToolResponse handle(JsonNode args, ToolContext ctx) {
        log.debug("Reasoning compare called with args: {}", args);
        
        // Validate input
        DeepToolInputValidator.ValidationResult validation = 
            DeepToolInputValidator.validateReasoningCompare(args);
        
        if (!validation.valid()) {
            return createErrorResponse("Validation failed: " + String.join(", ", validation.errors()));
        }
        
        try {
            List<String> items = new ArrayList<>();
            for (JsonNode item : args.get("items")) {
                items.add(item.asText());
            }
            
            String criteria = args.get("criteria").asText();
            
            // Perform comparison
            ComparisonResult comparison = performComparison(items, criteria);
            
            ObjectNode result = mapper.createObjectNode();
            result.put("comparison", comparison.analysis);
            result.put("winner", comparison.winner);
            result.put("confidence", comparison.confidence);
            
            log.info("Comparison completed: {} items compared, winner={}, confidence={}", 
                items.size(), comparison.winner, comparison.confidence);
            
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
            log.error("Reasoning compare failed: {}", e.getMessage(), e);
            return createErrorResponse(e.getMessage());
        }
    }
    
    private ComparisonResult performComparison(List<String> items, String criteria) {
        if (items.isEmpty()) {
            return new ComparisonResult("No items to compare", "", 0.0);
        }
        
        if (items.size() == 1) {
            return new ComparisonResult(
                "Only one item provided: " + truncate(items.get(0), 100),
                items.get(0),
                1.0
            );
        }
        
        // Simple heuristic comparison based on length and keyword matching
        StringBuilder analysis = new StringBuilder();
        analysis.append("Comparing ").append(items.size()).append(" items based on criteria: ")
            .append(truncate(criteria, 50)).append("\n\n");
        
        String criteriaLower = criteria.toLowerCase();
        int bestScore = -1;
        String bestItem = items.get(0);
        
        for (int i = 0; i < items.size(); i++) {
            String item = items.get(i);
            int score = calculateRelevanceScore(item, criteriaLower);
            
            analysis.append("Item ").append(i + 1).append(": ")
                .append(truncate(item, 50))
                .append(" (score: ").append(score).append(")\n");
            
            if (score > bestScore) {
                bestScore = score;
                bestItem = item;
            }
        }
        
        analysis.append("\nBest match: ").append(truncate(bestItem, 50));
        
        // Calculate confidence based on score difference
        double confidence = Math.min(1.0, 0.5 + (bestScore * 0.05));
        
        return new ComparisonResult(analysis.toString(), bestItem, confidence);
    }
    
    private int calculateRelevanceScore(String item, String criteriaLower) {
        int score = 0;
        String itemLower = item.toLowerCase();
        
        // Check for keyword matches
        String[] criteriaWords = criteriaLower.split("\\s+");
        for (String word : criteriaWords) {
            if (word.length() > 3 && itemLower.contains(word)) {
                score += 2;
            }
        }
        
        // Bonus for longer, more detailed items
        score += Math.min(5, item.length() / 50);
        
        return score;
    }
    
    private CallToolResponse createErrorResponse(String message) {
        return new CallToolResponse(
            false,
            name(),
            null,
            false,
            null,
            Instant.now(),
            new ToolError("COMPARE_ERROR", message, false)
        );
    }
    
    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
    
    private record ComparisonResult(String analysis, String winner, double confidence) {}
}
