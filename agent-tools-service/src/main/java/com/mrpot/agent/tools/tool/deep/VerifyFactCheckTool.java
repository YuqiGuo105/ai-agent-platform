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
 * Verify fact-check tool.
 * Checks claims against available evidence sources.
 */
@Slf4j
@Component
public class VerifyFactCheckTool implements ToolHandler {
    
    private static final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() {
        return "verify.fact_check";
    }
    
    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
            name(),
            "Verify claims against available evidence sources",
            "1.0.0",
            DeepToolSchemas.verifyFactCheckInput(),
            DeepToolSchemas.verifyFactCheckOutput(),
            null,
            60L
        );
    }
    
    @Override
    public CallToolResponse handle(JsonNode args, ToolContext ctx) {
        log.debug("Verify fact_check called with args: {}", args);
        
        // Validate input
        DeepToolInputValidator.ValidationResult validation = 
            DeepToolInputValidator.validateVerifyFactCheck(args);
        
        if (!validation.valid()) {
            return createErrorResponse("Validation failed: " + String.join(", ", validation.errors()));
        }
        
        try {
            List<String> claims = extractStringList(args.get("claims"));
            List<String> evidenceSources = args.has("evidenceSources") ? 
                extractStringList(args.get("evidenceSources")) : List.of();
            
            // Run fact-check
            List<FactualityFlagInfo> factualityFlags = new ArrayList<>();
            List<String> unresolvedClaims = new ArrayList<>();
            
            for (String claim : claims) {
                FactualityFlagInfo flag = checkClaim(claim, evidenceSources);
                factualityFlags.add(flag);
                
                if ("unverifiable".equals(flag.verdict)) {
                    unresolvedClaims.add(claim);
                }
            }
            
            // Build result
            ObjectNode result = mapper.createObjectNode();
            
            ArrayNode flagsArray = mapper.createArrayNode();
            for (FactualityFlagInfo flag : factualityFlags) {
                ObjectNode fNode = mapper.createObjectNode();
                fNode.put("claim", flag.claim);
                fNode.put("verdict", flag.verdict);
                fNode.put("confidence", flag.confidence);
                flagsArray.add(fNode);
            }
            result.set("factualityFlags", flagsArray);
            
            ArrayNode unresolvedArray = mapper.createArrayNode();
            unresolvedClaims.forEach(unresolvedArray::add);
            result.set("unresolvedClaims", unresolvedArray);
            
            log.info("Fact check completed: {} claims checked, {} unresolved", 
                claims.size(), unresolvedClaims.size());
            
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
            log.error("Verify fact_check failed: {}", e.getMessage(), e);
            return createErrorResponse(e.getMessage());
        }
    }
    
    private FactualityFlagInfo checkClaim(String claim, List<String> evidenceSources) {
        if (evidenceSources.isEmpty()) {
            // No evidence sources - claim is unverifiable
            return new FactualityFlagInfo(claim, "unverifiable", 0.3);
        }
        
        String claimLower = claim.toLowerCase();
        String[] claimWords = extractKeywords(claimLower);
        
        int matchCount = 0;
        int totalKeywords = claimWords.length;
        
        for (String source : evidenceSources) {
            String sourceLower = source.toLowerCase();
            
            for (String keyword : claimWords) {
                if (keyword.length() > 3 && sourceLower.contains(keyword)) {
                    matchCount++;
                }
            }
        }
        
        if (totalKeywords == 0) {
            return new FactualityFlagInfo(claim, "unverifiable", 0.3);
        }
        
        double matchRatio = (double) matchCount / totalKeywords;
        
        if (matchRatio >= 0.5) {
            // Good keyword match - claim is supported
            double confidence = Math.min(0.9, 0.5 + matchRatio * 0.4);
            return new FactualityFlagInfo(claim, "supported", confidence);
        } else if (matchRatio > 0) {
            // Partial match - still supported but lower confidence
            return new FactualityFlagInfo(claim, "supported", 0.4 + matchRatio * 0.2);
        } else {
            // No match - unverifiable
            return new FactualityFlagInfo(claim, "unverifiable", 0.3);
        }
    }
    
    private String[] extractKeywords(String text) {
        // Simple keyword extraction: split by whitespace and filter short words
        return text.split("\\s+");
    }
    
    private List<String> extractStringList(JsonNode arrayNode) {
        List<String> result = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode node : arrayNode) {
                if (node.isTextual()) {
                    result.add(node.asText());
                }
            }
        }
        return result;
    }
    
    private CallToolResponse createErrorResponse(String message) {
        return new CallToolResponse(
            false,
            name(),
            null,
            false,
            null,
            Instant.now(),
            new ToolError("FACT_CHECK_ERROR", message, false)
        );
    }
    
    private record FactualityFlagInfo(String claim, String verdict, double confidence) {}
}
