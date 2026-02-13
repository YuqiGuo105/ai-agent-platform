package com.mrpot.agent.service.pipeline.stages;

import com.mrpot.agent.common.deep.ReflectionNote;
import com.mrpot.agent.common.deep.VerificationReport;
import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.service.pipeline.PipelineContext;
import com.mrpot.agent.service.pipeline.Processor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
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
 * - Streams answer incrementally via answer_delta events for consistent UX with fast mode
 */
@Slf4j
public class DeepSynthesisStage implements Processor<Void, Flux<SseEnvelope>> {
    
    private static final String DEFAULT_DEEP_ANSWER = "DEEP mode answer";
    
    // Target characters per second for streaming (controls visual speed)
    private static final int CHARS_PER_SECOND = 80;
    
    // Minimum delay between chunks in milliseconds
    private static final long MIN_CHUNK_DELAY_MS = 10;
    
    // Pattern to detect and sanitize raw reasoning traces
    private static final Pattern REASONING_TRACE_PATTERN = Pattern.compile(
        "(?i)(hypothesis|reasoning step|confidence\\s*:|evidence\\s*refs|internal\\s*trace|chain\\s*of\\s*thought)",
        Pattern.CASE_INSENSITIVE
    );
    
    @Override
    public Mono<Flux<SseEnvelope>> process(Void input, PipelineContext context) {
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
            
            // Split answer into token-sized chunks for smooth streaming
            List<String> chunks = splitIntoTokens(finalAnswer);
            
            // Calculate delay per chunk to achieve target streaming speed
            // Total streaming time = answer length / CHARS_PER_SECOND seconds
            long totalStreamingTimeMs = (long) (finalAnswer.length() * 1000.0 / CHARS_PER_SECOND);
            long delayPerChunkMs = chunks.isEmpty() ? MIN_CHUNK_DELAY_MS : 
                Math.max(MIN_CHUNK_DELAY_MS, totalStreamingTimeMs / chunks.size());
            
            // Capture uiBlocks and unresolvedClaims for use in deferred synthesis event
            final List<Map<String, Object>> finalUiBlocks = uiBlocks;
            final List<String> finalUnresolvedClaims = unresolvedClaims;
            final long chunkDelay = delayPerChunkMs;
            
            // Create streaming flux of answer_delta events followed by deep_synthesis event
            Flux<SseEnvelope> answerDeltaEvents = Flux.fromIterable(chunks)
                .delayElements(Duration.ofMillis(chunkDelay))
                .map(chunk -> new SseEnvelope(
                    StageNames.ANSWER_DELTA,
                    chunk,
                    Map.of("delta", chunk),
                    context.nextSeq(),
                    System.currentTimeMillis(),
                    context.traceId(),
                    context.sessionId()
                ))
                .filter(envelope -> !envelope.message().isEmpty());
            
            // Create deferred deep_synthesis event - seq number assigned after all answer_delta events
            Flux<SseEnvelope> synthesisEvent = Flux.defer(() -> Flux.just(new SseEnvelope(
                StageNames.DEEP_SYNTHESIS,
                "Synthesis complete",
                Map.of(
                    "round", context.getCurrentRound(),
                    "status", "complete",
                    "summary", "Synthesis complete",
                    "hasUncertainty", !finalUnresolvedClaims.isEmpty(),
                    "unresolvedCount", finalUnresolvedClaims.size(),
                    "uiBlocks", finalUiBlocks
                ),
                context.nextSeq(),
                System.currentTimeMillis(),
                context.traceId(),
                context.sessionId()
            )));
            
            // Concatenate answer_delta events with final deep_synthesis event
            return answerDeltaEvents.concatWith(synthesisEvent);
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
            
            // Return error flux with single error envelope
            return Mono.just(Flux.just(new SseEnvelope(
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
            )));
        });
    }
    
    /**
     * Split text into token-sized chunks for smooth streaming.
     * Chinese characters are split individually.
     * English words are kept together (split at word boundaries).
     * Punctuation and whitespace are included with adjacent text.
     */
    private List<String> splitIntoTokens(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }
        
        StringBuilder currentToken = new StringBuilder();
        
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            String ch = text.substring(i, i + charCount);
            
            if (isCJKCharacter(codePoint)) {
                // CJK characters are emitted individually
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0);
                }
                tokens.add(ch);
            } else if (Character.isWhitespace(codePoint) || isPunctuation(codePoint)) {
                // Whitespace and punctuation end current token
                currentToken.append(ch);
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0);
                }
            } else {
                // ASCII letters/numbers - accumulate into word tokens
                currentToken.append(ch);
                // Emit token if it gets too long (max 4 chars for smooth effect)
                if (currentToken.length() >= 4) {
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0);
                }
            }
            
            i += charCount;
        }
        
        // Add remaining token
        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString());
        }
        
        return tokens;
    }
    
    /**
     * Check if a code point is a CJK (Chinese/Japanese/Korean) character.
     */
    private boolean isCJKCharacter(int codePoint) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
            || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
            || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
            || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
            || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C
            || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D
            || block == Character.UnicodeBlock.HIRAGANA
            || block == Character.UnicodeBlock.KATAKANA
            || block == Character.UnicodeBlock.HANGUL_SYLLABLES
            || block == Character.UnicodeBlock.HANGUL_JAMO
            || block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO
            || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
            || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }
    
    /**
     * Check if a code point is punctuation.
     */
    private boolean isPunctuation(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.CONNECTOR_PUNCTUATION
            || type == Character.DASH_PUNCTUATION
            || type == Character.END_PUNCTUATION
            || type == Character.FINAL_QUOTE_PUNCTUATION
            || type == Character.INITIAL_QUOTE_PUNCTUATION
            || type == Character.OTHER_PUNCTUATION
            || type == Character.START_PUNCTUATION;
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
