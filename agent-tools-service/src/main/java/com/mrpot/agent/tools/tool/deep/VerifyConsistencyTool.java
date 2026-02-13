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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Verify consistency tool.
 * Checks reasoning artifacts for consistency and detects contradictions.
 */
@Slf4j
@Component
public class VerifyConsistencyTool implements ToolHandler {
    
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final double CONFIDENCE_FLUCTUATION_THRESHOLD = 0.3;
    
    @Override
    public String name() {
        return "verify.consistency";
    }
    
    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
            name(),
            "Check reasoning artifacts for consistency and detect contradictions",
            "1.0.0",
            DeepToolSchemas.verifyConsistencyInput(),
            DeepToolSchemas.verifyConsistencyOutput(),
            null,
            60L
        );
    }
    
    @Override
    public CallToolResponse handle(JsonNode args, ToolContext ctx) {
        log.debug("Verify consistency called with args: {}", args);
        
        // Validate input
        DeepToolInputValidator.ValidationResult validation = 
            DeepToolInputValidator.validateVerifyConsistency(args);
        
        if (!validation.valid()) {
            return createErrorResponse("Validation failed: " + String.join(", ", validation.errors()));
        }
        
        try {
            JsonNode reasoningArtifacts = args.get("reasoningArtifacts");
            
            // Run consistency checks
            List<ContradictionInfo> contradictions = new ArrayList<>();
            List<String> flags = new ArrayList<>();
            
            // Check for confidence fluctuations
            checkConfidenceFluctuations(reasoningArtifacts, contradictions, flags);
            
            // Check for duplicate hypotheses
            checkDuplicateHypotheses(reasoningArtifacts, contradictions, flags);
            
            // Calculate consistency score
            double consistencyScore = calculateConsistencyScore(contradictions.size(), reasoningArtifacts.size());
            
            // Build result
            ObjectNode result = mapper.createObjectNode();
            result.put("consistencyScore", consistencyScore);
            
            ArrayNode contradictionsArray = mapper.createArrayNode();
            for (ContradictionInfo c : contradictions) {
                ObjectNode cNode = mapper.createObjectNode();
                cNode.put("stepA", c.stepA);
                cNode.put("stepB", c.stepB);
                cNode.put("description", c.description);
                contradictionsArray.add(cNode);
            }
            result.set("contradictions", contradictionsArray);
            
            ArrayNode flagsArray = mapper.createArrayNode();
            flags.forEach(flagsArray::add);
            result.set("flags", flagsArray);
            
            log.info("Consistency check completed: score={}, contradictions={}, flags={}", 
                consistencyScore, contradictions.size(), flags.size());
            
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
            log.error("Verify consistency failed: {}", e.getMessage(), e);
            return createErrorResponse(e.getMessage());
        }
    }
    
    private void checkConfidenceFluctuations(JsonNode artifacts, List<ContradictionInfo> contradictions, List<String> flags) {
        for (int i = 1; i < artifacts.size(); i++) {
            JsonNode prevStep = artifacts.get(i - 1);
            JsonNode currStep = artifacts.get(i);
            
            double prevConfidence = getConfidence(prevStep);
            double currConfidence = getConfidence(currStep);
            
            double fluctuation = Math.abs(currConfidence - prevConfidence);
            
            if (fluctuation > CONFIDENCE_FLUCTUATION_THRESHOLD) {
                contradictions.add(new ContradictionInfo(
                    i - 1,
                    i,
                    String.format("Large confidence fluctuation: %.2f -> %.2f (diff: %.2f)", 
                        prevConfidence, currConfidence, fluctuation)
                ));
                flags.add("CONFIDENCE_FLUCTUATION_DETECTED");
            }
        }
    }
    
    private void checkDuplicateHypotheses(JsonNode artifacts, List<ContradictionInfo> contradictions, List<String> flags) {
        Set<String> seenHypotheses = new HashSet<>();
        
        for (int i = 0; i < artifacts.size(); i++) {
            JsonNode step = artifacts.get(i);
            String hypothesis = getHypothesis(step);
            
            if (hypothesis != null && !hypothesis.isBlank()) {
                String normalized = normalizeHypothesis(hypothesis);
                
                if (seenHypotheses.contains(normalized)) {
                    // Find the previous step with same hypothesis
                    int prevIndex = findPreviousHypothesisIndex(artifacts, normalized, i);
                    contradictions.add(new ContradictionInfo(
                        prevIndex,
                        i,
                        "Duplicate hypothesis detected: " + truncate(hypothesis, 50)
                    ));
                    flags.add("DUPLICATE_HYPOTHESIS_DETECTED");
                }
                
                seenHypotheses.add(normalized);
            }
        }
    }
    
    private int findPreviousHypothesisIndex(JsonNode artifacts, String normalizedHypothesis, int currentIndex) {
        for (int i = 0; i < currentIndex; i++) {
            String hypothesis = getHypothesis(artifacts.get(i));
            if (hypothesis != null && normalizeHypothesis(hypothesis).equals(normalizedHypothesis)) {
                return i;
            }
        }
        return 0;
    }
    
    private double calculateConsistencyScore(int contradictionCount, int artifactCount) {
        if (artifactCount == 0) {
            return 1.0;
        }
        
        if (contradictionCount == 0) {
            return 1.0;
        }
        
        // Deduct 0.15 per contradiction, minimum 0.0
        double penalty = contradictionCount * 0.15;
        return Math.max(0.0, 1.0 - penalty);
    }
    
    private double getConfidence(JsonNode step) {
        if (step.has("confidence") && step.get("confidence").isNumber()) {
            return step.get("confidence").asDouble();
        }
        return 0.5; // Default confidence
    }
    
    private String getHypothesis(JsonNode step) {
        if (step.has("hypothesis") && step.get("hypothesis").isTextual()) {
            return step.get("hypothesis").asText();
        }
        return null;
    }
    
    private String normalizeHypothesis(String hypothesis) {
        // Simple normalization: lowercase and remove extra whitespace
        return hypothesis.toLowerCase().trim().replaceAll("\\s+", " ");
    }
    
    private CallToolResponse createErrorResponse(String message) {
        return new CallToolResponse(
            false,
            name(),
            null,
            false,
            null,
            Instant.now(),
            new ToolError("CONSISTENCY_ERROR", message, false)
        );
    }
    
    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
    
    private record ContradictionInfo(int stepA, int stepB, String description) {}
}
