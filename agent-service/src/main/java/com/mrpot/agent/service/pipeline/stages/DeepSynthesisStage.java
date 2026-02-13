package com.mrpot.agent.service.pipeline.stages;

import com.mrpot.agent.common.deep.ReflectionNote;
import com.mrpot.agent.common.deep.VerificationReport;
import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.service.pipeline.PipelineContext;
import com.mrpot.agent.service.pipeline.Processor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Deep synthesis stage - synthesizes the final answer from reasoning results.
 * 
 * This stage combines reasoning outputs and produces the final response.
 * Features:
 * - Reads reflection note and verification report
 * - Generates conclusion with evidence sources
 * - Adds uncertainty declaration for unresolved claims
 * - Sanitizes raw reasoning traces (filters internal keywords)
 * - Produces UI blocks for structured display
 */
@Slf4j
public class DeepSynthesisStage implements Processor<Void, SseEnvelope> {
    
    private static final String DEFAULT_DEEP_ANSWER = "DEEP mode answer";
    
    // Pattern to detect and sanitize raw reasoning traces
    private static final Pattern REASONING_TRACE_PATTERN = Pattern.compile(
        "(?i)(hypothesis|reasoning step|confidence\\s*:|evidence\\s*refs|internal\\s*trace|chain\\s*of\\s*thought)",
        Pattern.CASE_INSENSITIVE
    );
    
    @Override
    public Mono<SseEnvelope> process(Void input, PipelineContext context) {
        log.debug("Starting deep synthesis stage for runId={}", context.runId());
        
        return Mono.fromSupplier(() -> {
            // Get reasoning result from context
            Map<String, Object> reasoning = context.getDeepReasoning();
            
            // Get reflection note and verification report
            ReflectionNote reflectionNote = context.getReflectionNote();
            VerificationReport verificationReport = context.getVerificationReport();
            
            if (reasoning == null) {
                log.warn("No reasoning result found in context for runId={}, using default synthesis", 
                    context.runId());
            }
            
            // Build conclusion text
            String conclusionText = buildConclusion(reasoning);
            
            // Build evidence summaries
            List<String> evidenceSummaries = buildEvidenceSummaries(reasoning);
            
            // Get unresolved claims
            List<String> unresolvedClaims = verificationReport != null && verificationReport.unresolvedClaims() != null
                ? verificationReport.unresolvedClaims()
                : List.of();
            
            // Build final answer with structured format
            String finalAnswer = buildFinalAnswer(conclusionText, evidenceSummaries, unresolvedClaims);
            
            // Sanitize to remove raw reasoning traces
            finalAnswer = sanitizeAnswer(finalAnswer);
            
            // Store final answer in context
            context.setFinalAnswer(finalAnswer);
            
            // Build UI blocks
            List<Map<String, Object>> uiBlocks = buildUiBlocks(conclusionText, evidenceSummaries, unresolvedClaims);
            context.setSynthesisBlocks(uiBlocks);
            
            // Build synthesis result DTO
            Map<String, Object> synthesisDto = Map.of(
                "round", context.getCurrentRound(),
                "status", "complete",
                "summary", "Synthesis complete",
                "answerLength", finalAnswer.length(),
                "hasUncertainty", !unresolvedClaims.isEmpty(),
                "completedAt", System.currentTimeMillis()
            );
            
            // Store synthesis result in context
            context.setDeepSynthesis(synthesisDto);
            
            log.info("Deep synthesis completed for runId={}: answer length={}, unresolved claims={}", 
                context.runId(), finalAnswer.length(), unresolvedClaims.size());
            
            // Create SSE envelope with synthesis result
            return new SseEnvelope(
                StageNames.DEEP_SYNTHESIS,
                "Synthesis complete",
                Map.of(
                    "round", context.getCurrentRound(),
                    "status", "complete",
                    "summary", "Synthesis complete",
                    "hasUncertainty", !unresolvedClaims.isEmpty(),
                    "unresolvedCount", unresolvedClaims.size(),
                    "uiBlocks", uiBlocks
                ),
                context.nextSeq(),
                System.currentTimeMillis(),
                context.traceId(),
                context.sessionId()
            );
        }).onErrorResume(e -> {
            log.error("Failed to complete deep synthesis for runId={}: {}", 
                context.runId(), e.getMessage(), e);
            
            // Store error state in context
            context.setDeepSynthesis(Map.of(
                "round", context.getCurrentRound(),
                "status", "error",
                "summary", "Synthesis failed: " + (e.getMessage() != null ? e.getMessage() : "Unknown error")
            ));
            
            // Set a fallback answer
            context.setFinalAnswer("Error generating response");
            context.setSynthesisBlocks(List.of());
            
            // Return error indicator envelope
            return Mono.just(new SseEnvelope(
                StageNames.DEEP_SYNTHESIS,
                "Synthesis failed",
                Map.of(
                    "round", context.getCurrentRound(),
                    "status", "error",
                    "summary", "Synthesis failed: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"),
                    "uiBlocks", List.of()
                ),
                context.nextSeq(),
                System.currentTimeMillis(),
                context.traceId(),
                context.sessionId()
            ));
        });
    }
    
    /**
     * Build the conclusion text from reasoning data.
     */
    private String buildConclusion(Map<String, Object> reasoning) {
        if (reasoning == null) {
            return DEFAULT_DEEP_ANSWER;
        }
        
        Object fullResponse = reasoning.get("fullResponse");
        if (fullResponse != null && !fullResponse.toString().isBlank()) {
            return fullResponse.toString();
        }
        
        Object hypothesis = reasoning.get("hypothesis");
        if (hypothesis != null && !hypothesis.toString().isBlank()) {
            return hypothesis.toString();
        }
        
        Object finalHypothesis = reasoning.get("finalHypothesis");
        if (finalHypothesis != null && !finalHypothesis.toString().isBlank()) {
            return finalHypothesis.toString();
        }
        
        Object summary = reasoning.get("summary");
        if (summary != null && !summary.toString().isBlank()) {
            return summary.toString();
        }
        
        return DEFAULT_DEEP_ANSWER;
    }
    
    /**
     * Build evidence summaries from reasoning data.
     */
    @SuppressWarnings("unchecked")
    private List<String> buildEvidenceSummaries(Map<String, Object> reasoning) {
        List<String> summaries = new ArrayList<>();
        
        if (reasoning == null) {
            return summaries;
        }
        
        // Try to extract evidence sources
        Object steps = reasoning.get("steps");
        if (steps instanceof List) {
            List<?> stepsList = (List<?>) steps;
            for (int i = 0; i < Math.min(stepsList.size(), 3); i++) { // Limit to top 3
                Object step = stepsList.get(i);
                if (step instanceof Map) {
                    Map<?, ?> stepMap = (Map<?, ?>) step;
                    Object evidenceRefs = stepMap.get("evidenceRefs");
                    if (evidenceRefs instanceof List) {
                        List<?> refs = (List<?>) evidenceRefs;
                        for (Object ref : refs) {
                            if (ref != null && !ref.toString().isBlank()) {
                                summaries.add(sanitizeEvidence(ref.toString()));
                            }
                        }
                    }
                }
            }
        }
        
        // Deduplicate and limit
        return summaries.stream()
            .distinct()
            .limit(5)
            .toList();
    }
    
    /**
     * Build the structured final answer.
     */
    private String buildFinalAnswer(String conclusion, List<String> evidenceSources, List<String> unresolvedClaims) {
        StringBuilder answer = new StringBuilder();
        
        // Conclusion section
        answer.append("[Conclusion]\n");
        answer.append(conclusion);
        answer.append("\n\n");
        
        // Evidence sources section
        if (!evidenceSources.isEmpty()) {
            answer.append("[Evidence Sources]\n");
            for (int i = 0; i < evidenceSources.size(); i++) {
                answer.append("- Source ").append(i + 1).append(": ").append(evidenceSources.get(i)).append("\n");
            }
            answer.append("\n");
        }
        
        // Uncertainty declaration section
        if (!unresolvedClaims.isEmpty()) {
            answer.append("[Uncertainty Declaration]\n");
            answer.append("The following issues have not been fully resolved:\n");
            for (String claim : unresolvedClaims) {
                answer.append("- ").append(claim).append("\n");
            }
        }
        
        return answer.toString().trim();
    }
    
    /**
     * Build UI blocks for structured display.
     */
    private List<Map<String, Object>> buildUiBlocks(String conclusion, List<String> evidenceSources, List<String> unresolvedClaims) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        
        // Conclusion block
        blocks.add(Map.of(
            "type", "conclusion",
            "content", conclusion
        ));
        
        // Evidence block (if any)
        if (!evidenceSources.isEmpty()) {
            blocks.add(Map.of(
                "type", "evidence",
                "sources", evidenceSources
            ));
        }
        
        // Uncertainty block (if any)
        if (!unresolvedClaims.isEmpty()) {
            blocks.add(Map.of(
                "type", "uncertainty",
                "claims", unresolvedClaims
            ));
        }
        
        return blocks;
    }
    
    /**
     * Sanitize the final answer to remove raw reasoning traces.
     */
    private String sanitizeAnswer(String answer) {
        if (answer == null) {
            return "";
        }
        
        String[] lines = answer.split("\n");
        StringBuilder sanitized = new StringBuilder();
        boolean insideCodeBlock = false;
        
        for (String line : lines) {
            if (line.trim().startsWith("```")) {
                insideCodeBlock = !insideCodeBlock;
            }
            if (insideCodeBlock || !REASONING_TRACE_PATTERN.matcher(line).find()) {
                sanitized.append(line).append("\n");
            }
        }
        
        return sanitized.toString().trim();
    }
    
    /**
     * Sanitize individual evidence references.
     */
    private String sanitizeEvidence(String evidence) {
        if (evidence == null) {
            return "";
        }
        
        // Remove any internal trace keywords
        String sanitized = REASONING_TRACE_PATTERN.matcher(evidence).replaceAll("");
        
        // Truncate if too long
        if (sanitized.length() > 100) {
            sanitized = sanitized.substring(0, 100) + "...";
        }
        
        return sanitized.trim();
    }
}
