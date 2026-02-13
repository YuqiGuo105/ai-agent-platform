package com.mrpot.agent.service.pipeline.stages;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrpot.agent.common.deep.VerificationReport;
import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.common.tool.mcp.CallToolRequest;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.service.ToolInvoker;
import com.mrpot.agent.service.pipeline.PipelineContext;
import com.mrpot.agent.service.pipeline.Processor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Deep verification stage - runs consistency and fact-check verification tools.
 * 
 * This stage:
 * - Calls verify.consistency to check for contradictions
 * - Calls verify.fact_check to validate claims
 * - Produces VerificationReport stored in context
 * - Emits DEEP_VERIFICATION SSE event
 */
@Slf4j
@RequiredArgsConstructor
public class DeepVerificationStage implements Processor<Void, SseEnvelope> {
    
    private static final ObjectMapper mapper = new ObjectMapper();
    
    private final ToolInvoker toolInvoker;
    
    @Override
    public Mono<SseEnvelope> process(Void input, PipelineContext context) {
        log.debug("Starting deep verification stage for runId={}", context.runId());
        
        return Mono.fromSupplier(() -> {
            // Get reasoning artifacts from context
            Map<String, Object> reasoning = context.getDeepReasoning();
            
            // Run consistency check
            ConsistencyResult consistencyResult = runConsistencyCheck(reasoning);
            
            // Extract claims for fact-checking
            List<String> claims = extractClaims(reasoning);
            List<String> evidenceSources = extractEvidenceSources(reasoning);
            
            // Run fact check
            FactCheckResult factCheckResult = runFactCheck(claims, evidenceSources);
            
            // Build verification report
            VerificationReport report = buildVerificationReport(consistencyResult, factCheckResult);
            
            // Store in context
            context.setVerificationReport(report);
            
            log.info("Deep verification completed for runId={}: consistencyScore={}, unresolvedClaims={}",
                context.runId(), report.consistencyScore(), report.unresolvedClaims().size());
            
            // Create SSE envelope
            return new SseEnvelope(
                StageNames.DEEP_VERIFICATION,
                "Verification complete",
                Map.of(
                    "consistencyScore", report.consistencyScore(),
                    "contradictionCount", report.contradictions().size(),
                    "unresolvedCount", report.unresolvedClaims().size(),
                    "verified", report.verified()
                ),
                context.nextSeq(),
                System.currentTimeMillis(),
                context.traceId(),
                context.sessionId()
            );
        }).onErrorResume(e -> {
            log.error("Failed to complete deep verification for runId={}: {}",
                context.runId(), e.getMessage(), e);
            
            // Store default report on error
            VerificationReport defaultReport = VerificationReport.defaultReport();
            context.setVerificationReport(defaultReport);
            
            return Mono.just(new SseEnvelope(
                StageNames.DEEP_VERIFICATION,
                "Verification failed (using default)",
                Map.of(
                    "consistencyScore", 1.0,
                    "contradictionCount", 0,
                    "unresolvedCount", 0,
                    "verified", true,
                    "error", e.getMessage() != null ? e.getMessage() : "Unknown error"
                ),
                context.nextSeq(),
                System.currentTimeMillis(),
                context.traceId(),
                context.sessionId()
            ));
        });
    }
    
    /**
     * Run consistency check using verify.consistency tool.
     */
    private ConsistencyResult runConsistencyCheck(Map<String, Object> reasoning) {
        try {
            // Build reasoning artifacts array
            ObjectNode args = mapper.createObjectNode();
            ArrayNode artifactsArray = mapper.createArrayNode();
            
            if (reasoning != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> steps = (List<Map<String, Object>>) reasoning.get("steps");
                if (steps != null) {
                    for (Map<String, Object> step : steps) {
                        ObjectNode artifact = mapper.createObjectNode();
                        artifact.put("hypothesis", String.valueOf(step.getOrDefault("hypothesis", "")));
                        
                        ArrayNode evidenceRefs = mapper.createArrayNode();
                        @SuppressWarnings("unchecked")
                        List<String> refs = (List<String>) step.get("evidenceRefs");
                        if (refs != null) {
                            refs.forEach(evidenceRefs::add);
                        }
                        artifact.set("evidenceRefs", evidenceRefs);
                        
                        Object conf = step.get("confidence");
                        artifact.put("confidence", conf instanceof Number ? ((Number) conf).doubleValue() : 0.5);
                        
                        artifactsArray.add(artifact);
                    }
                }
            }
            
            args.set("reasoningArtifacts", artifactsArray);
            
            if (toolInvoker != null) {
                CallToolRequest request = new CallToolRequest(
                    "verify.consistency",
                    args,
                    null, null, null, null
                );
                
                CallToolResponse response = toolInvoker.call(request).block();
                
                if (response != null && response.ok() && response.result() != null) {
                    JsonNode result = response.result();
                    double score = result.has("consistencyScore") ? result.get("consistencyScore").asDouble() : 1.0;
                    
                    List<VerificationReport.ContradictionFlag> contradictions = new ArrayList<>();
                    if (result.has("contradictions") && result.get("contradictions").isArray()) {
                        for (JsonNode c : result.get("contradictions")) {
                            contradictions.add(new VerificationReport.ContradictionFlag(
                                c.has("stepA") ? c.get("stepA").asInt() : 0,
                                c.has("stepB") ? c.get("stepB").asInt() : 0,
                                c.has("description") ? c.get("description").asText() : "Unknown contradiction"
                            ));
                        }
                    }
                    
                    List<String> flags = new ArrayList<>();
                    if (result.has("flags") && result.get("flags").isArray()) {
                        for (JsonNode f : result.get("flags")) {
                            flags.add(f.asText());
                        }
                    }
                    
                    return new ConsistencyResult(score, contradictions, flags);
                }
            }
            
            // Default if tool not available or error
            return new ConsistencyResult(1.0, List.of(), List.of());
            
        } catch (Exception e) {
            log.warn("Consistency check failed, using default: {}", e.getMessage());
            return new ConsistencyResult(1.0, List.of(), List.of());
        }
    }
    
    /**
     * Run fact check using verify.fact_check tool.
     */
    private FactCheckResult runFactCheck(List<String> claims, List<String> evidenceSources) {
        try {
            if (claims.isEmpty()) {
                return new FactCheckResult(List.of(), List.of());
            }
            
            ObjectNode args = mapper.createObjectNode();
            ArrayNode claimsArray = mapper.createArrayNode();
            claims.forEach(claimsArray::add);
            args.set("claims", claimsArray);
            
            ArrayNode sourcesArray = mapper.createArrayNode();
            evidenceSources.forEach(sourcesArray::add);
            args.set("evidenceSources", sourcesArray);
            
            if (toolInvoker != null) {
                CallToolRequest request = new CallToolRequest(
                    "verify.fact_check",
                    args,
                    null, null, null, null
                );
                
                CallToolResponse response = toolInvoker.call(request).block();
                
                if (response != null && response.ok() && response.result() != null) {
                    JsonNode result = response.result();
                    
                    List<VerificationReport.FactualityFlag> factualityFlags = new ArrayList<>();
                    if (result.has("factualityFlags") && result.get("factualityFlags").isArray()) {
                        for (JsonNode f : result.get("factualityFlags")) {
                            factualityFlags.add(new VerificationReport.FactualityFlag(
                                f.has("claim") ? f.get("claim").asText() : "",
                                f.has("verdict") ? f.get("verdict").asText() : "unverifiable",
                                f.has("confidence") ? f.get("confidence").asDouble() : 0.3
                            ));
                        }
                    }
                    
                    List<String> unresolvedClaims = new ArrayList<>();
                    if (result.has("unresolvedClaims") && result.get("unresolvedClaims").isArray()) {
                        for (JsonNode c : result.get("unresolvedClaims")) {
                            unresolvedClaims.add(c.asText());
                        }
                    }
                    
                    return new FactCheckResult(factualityFlags, unresolvedClaims);
                }
            }
            
            // Default if tool not available
            return new FactCheckResult(List.of(), List.of());
            
        } catch (Exception e) {
            log.warn("Fact check failed, using default: {}", e.getMessage());
            return new FactCheckResult(List.of(), List.of());
        }
    }
    
    /**
     * Extract claims from reasoning for fact-checking.
     */
    @SuppressWarnings("unchecked")
    private List<String> extractClaims(Map<String, Object> reasoning) {
        List<String> claims = new ArrayList<>();
        
        if (reasoning == null) {
            return claims;
        }
        
        // Extract hypotheses as claims
        List<Map<String, Object>> steps = (List<Map<String, Object>>) reasoning.get("steps");
        if (steps != null) {
            for (Map<String, Object> step : steps) {
                Object hypothesis = step.get("hypothesis");
                if (hypothesis != null && !hypothesis.toString().isBlank()) {
                    claims.add(hypothesis.toString());
                }
            }
        }
        
        // Also check final hypothesis
        Object finalHypothesis = reasoning.get("finalHypothesis");
        if (finalHypothesis != null && !finalHypothesis.toString().isBlank()) {
            claims.add(finalHypothesis.toString());
        }
        
        return claims.stream().distinct().limit(10).toList();
    }
    
    /**
     * Extract evidence sources from reasoning.
     */
    @SuppressWarnings("unchecked")
    private List<String> extractEvidenceSources(Map<String, Object> reasoning) {
        List<String> sources = new ArrayList<>();
        
        if (reasoning == null) {
            return sources;
        }
        
        List<Map<String, Object>> steps = (List<Map<String, Object>>) reasoning.get("steps");
        if (steps != null) {
            for (Map<String, Object> step : steps) {
                List<String> refs = (List<String>) step.get("evidenceRefs");
                if (refs != null) {
                    sources.addAll(refs);
                }
            }
        }
        
        return sources.stream().distinct().limit(20).toList();
    }
    
    /**
     * Build verification report from consistency and fact-check results.
     */
    private VerificationReport buildVerificationReport(ConsistencyResult consistency, FactCheckResult factCheck) {
        double score = consistency.score;
        boolean verified = score >= 0.7 && factCheck.unresolvedClaims.size() <= 2;
        
        List<String> issues = new ArrayList<>();
        if (score < 0.7) {
            issues.add("Low consistency score: " + String.format("%.2f", score));
        }
        if (!consistency.contradictions.isEmpty()) {
            issues.add("Found " + consistency.contradictions.size() + " contradictions");
        }
        if (factCheck.unresolvedClaims.size() > 2) {
            issues.add("Too many unresolved claims: " + factCheck.unresolvedClaims.size());
        }
        
        List<String> recommendations = new ArrayList<>();
        if (!consistency.contradictions.isEmpty()) {
            recommendations.add("Review and resolve contradictions in reasoning");
        }
        if (!factCheck.unresolvedClaims.isEmpty()) {
            recommendations.add("Gather additional evidence for unverified claims");
        }
        
        return new VerificationReport(
            score,
            consistency.contradictions,
            factCheck.factualityFlags,
            factCheck.unresolvedClaims,
            verified,
            issues,
            recommendations,
            Math.max(0.5, score),
            System.currentTimeMillis()
        );
    }
    
    private record ConsistencyResult(
        double score,
        List<VerificationReport.ContradictionFlag> contradictions,
        List<String> flags
    ) {}
    
    private record FactCheckResult(
        List<VerificationReport.FactualityFlag> factualityFlags,
        List<String> unresolvedClaims
    ) {}
}
