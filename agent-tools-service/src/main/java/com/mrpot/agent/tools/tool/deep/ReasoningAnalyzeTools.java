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
 * Reasoning analysis tool.
 * Analyzes data or evidence based on a guiding question.
 */
@Slf4j
@Component
public class ReasoningAnalyzeTools implements ToolHandler {
    
    private static final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() {
        return "reasoning.analyze";
    }
    
    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
            name(),
            "Analyze data or evidence based on a guiding question",
            "1.0.0",
            DeepToolSchemas.reasoningAnalyzeInput(),
            DeepToolSchemas.reasoningAnalyzeOutput(),
            null,
            90L
        );
    }
    
    @Override
    public CallToolResponse handle(JsonNode args, ToolContext ctx) {
        log.debug("Reasoning analyze called with args: {}", args);
        
        // Validate input
        DeepToolInputValidator.ValidationResult validation = 
            DeepToolInputValidator.validateReasoningAnalyze(args);
        
        if (!validation.valid()) {
            return createErrorResponse("Validation failed: " + String.join(", ", validation.errors()));
        }
        
        try {
            String data = args.get("data").asText();
            String question = args.get("question").asText();
            
            // Perform analysis
            AnalysisResult analysis = performAnalysis(data, question);
            
            ObjectNode result = mapper.createObjectNode();
            result.put("analysis", analysis.analysis);
            
            ArrayNode insightsArray = mapper.createArrayNode();
            analysis.insights.forEach(insightsArray::add);
            result.set("insights", insightsArray);
            
            result.put("confidence", analysis.confidence);
            
            log.info("Analysis completed: {} insights extracted, confidence={}", 
                analysis.insights.size(), analysis.confidence);
            
            return new CallToolResponse(
                true,
                name(),
                result,
                false,
                90L,
                Instant.now(),
                null
            );
            
        } catch (Exception e) {
            log.error("Reasoning analyze failed: {}", e.getMessage(), e);
            return createErrorResponse(e.getMessage());
        }
    }
    
    private AnalysisResult performAnalysis(String data, String question) {
        List<String> insights = new ArrayList<>();
        StringBuilder analysis = new StringBuilder();
        
        analysis.append("Analysis of data for question: ").append(truncate(question, 50)).append("\n\n");
        
        String dataLower = data.toLowerCase();
        String questionLower = question.toLowerCase();
        
        // Extract basic statistics
        int wordCount = data.split("\\s+").length;
        int sentenceCount = data.split("[.!?]").length;
        
        analysis.append("Data overview: ").append(wordCount).append(" words, ")
            .append(sentenceCount).append(" sentences.\n\n");
        
        insights.add("Data contains " + wordCount + " words");
        
        // Check for question-related keywords
        String[] questionWords = questionLower.split("\\s+");
        List<String> matches = new ArrayList<>();
        
        for (String word : questionWords) {
            if (word.length() > 3 && dataLower.contains(word)) {
                matches.add(word);
            }
        }
        
        if (!matches.isEmpty()) {
            analysis.append("Found relevant terms: ").append(String.join(", ", matches)).append("\n");
            insights.add("Found " + matches.size() + " relevant terms from question");
        }
        
        // Simple pattern detection
        if (dataLower.contains("because") || dataLower.contains("therefore")) {
            insights.add("Data contains causal reasoning");
            analysis.append("Causal relationships detected in data.\n");
        }
        
        if (dataLower.contains("however") || dataLower.contains("but") || dataLower.contains("although")) {
            insights.add("Data contains contrasting viewpoints");
            analysis.append("Contrasting viewpoints present.\n");
        }
        
        if (data.contains("1.") || data.contains("•") || data.contains("-")) {
            insights.add("Data appears to be structured/listed");
            analysis.append("Structured/listed format detected.\n");
        }
        
        // Calculate confidence based on relevance
        double confidence = calculateConfidence(matches.size(), wordCount, insights.size());
        
        analysis.append("\nOverall assessment: ");
        if (confidence > 0.7) {
            analysis.append("High relevance to the question.");
        } else if (confidence > 0.4) {
            analysis.append("Moderate relevance to the question.");
        } else {
            analysis.append("Limited direct relevance to the question.");
        }
        
        return new AnalysisResult(analysis.toString(), insights, confidence);
    }
    
    private double calculateConfidence(int matchCount, int wordCount, int insightCount) {
        double baseConfidence = 0.3;
        
        // Boost for keyword matches
        baseConfidence += Math.min(0.3, matchCount * 0.05);
        
        // Boost for insights found
        baseConfidence += Math.min(0.2, insightCount * 0.05);
        
        // Slight boost for reasonable data length
        if (wordCount > 20 && wordCount < 500) {
            baseConfidence += 0.1;
        }
        
        return Math.min(1.0, baseConfidence);
    }
    
    private CallToolResponse createErrorResponse(String message) {
        return new CallToolResponse(
            false,
            name(),
            null,
            false,
            null,
            Instant.now(),
            new ToolError("ANALYZE_ERROR", message, false)
        );
    }
    
    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
    
    private record AnalysisResult(String analysis, List<String> insights, double confidence) {}
}
