package com.mrpot.agent.service.pipeline;

import com.mrpot.agent.common.policy.ExecutionPolicy;
import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.model.ChatMessage;
import com.mrpot.agent.service.LlmService;
import com.mrpot.agent.service.pipeline.artifacts.DeepPlan;
import com.mrpot.agent.service.pipeline.artifacts.ReasoningStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Coordinates multi-round deep reasoning with bounded iterations.
 * 
 * <p>Implements a micro-iteration loop that:
 * <ul>
 *   <li>Enforces max rounds = min(policy.maxToolRounds(), config.maxRoundsCap())</li>
 *   <li>Stops early when confidence threshold is reached</li>
 *   <li>Detects lack of progress using hypothesis similarity</li>
 *   <li>Handles timeout with graceful degradation</li>
 *   <li>Emits DEEP_REASONING_STEP per round</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeepReasoningCoordinator {
    
    private static final String REASONING_PROMPT_TEMPLATE = """
        You are performing deep reasoning on a question.
        
        Plan objective: %s
        Subtasks: %s
        Current round: %d of %d
        Previous reasoning: %s
        
        Question: %s
        
        Think step by step. After your reasoning, provide:
        HYPOTHESIS: <your current best answer/conclusion>
        CONFIDENCE: <0.0 to 1.0, how confident you are>
        EVIDENCE: <comma-separated evidence supporting your hypothesis>
        """;
    
    private static final Pattern HYPOTHESIS_PATTERN = Pattern.compile("HYPOTHESIS:\\s*(.+?)(?=CONFIDENCE|$)", Pattern.DOTALL);
    private static final Pattern PRE_HYPOTHESIS_PATTERN = Pattern.compile("^(.+?)(?=HYPOTHESIS:)", Pattern.DOTALL);
    private static final Pattern CONFIDENCE_PATTERN = Pattern.compile("CONFIDENCE:\\s*([0-9.]+)");
    private static final Pattern EVIDENCE_PATTERN = Pattern.compile("EVIDENCE:\\s*(.+?)$", Pattern.DOTALL);
    
    private final LlmService llmService;
    private final DeepModeConfig config;
    
    /**
     * Result of executing reasoning rounds.
     */
    public record ReasoningResult(
        List<ReasoningStep> steps,
        String finalHypothesis,
        double finalConfidence,
        StopReason stopReason
    ) {}
    
    public enum StopReason {
        CONFIDENCE_REACHED,
        MAX_ROUNDS,
        NO_PROGRESS,
        TIMEOUT,
        ERROR
    }
    
    /**
     * Execute bounded reasoning loop.
     * 
     * @param context Pipeline context
     * @param plan The deep plan to execute
     * @param question User question
     * @param history Conversation history
     * @param policy Execution policy
     * @param eventSink Sink for emitting SSE events
     * @return Mono of final reasoning result
     */
    public Mono<ReasoningResult> executeReasoning(
            PipelineContext context,
            DeepPlan plan,
            String question,
            List<ChatMessage> history,
            ExecutionPolicy policy,
            Sinks.Many<SseEnvelope> eventSink) {
        
        // Calculate effective max rounds
        int maxRounds = Math.min(
            policy != null ? policy.maxToolRounds() : config.getMaxRoundsCap(),
            config.getMaxRoundsCap()
        );
        maxRounds = Math.max(1, maxRounds); // At least 1 round
        
        log.info("Starting deep reasoning for runId={}, maxRounds={}", context.runId(), maxRounds);
        
        DeepArtifactStore store = new DeepArtifactStore(context);
        
        return executeRound(context, store, plan, question, history, 1, maxRounds, eventSink)
            .timeout(Duration.ofSeconds(config.getReasoningTimeoutSeconds()))
            .onErrorResume(e -> {
                log.error("Reasoning timeout/error for runId={}: {}", context.runId(), e.getMessage());
                
                // Return whatever progress we have
                List<ReasoningStep> steps = store.getReasoningSteps();
                ReasoningStep last = store.getLastReasoningStep();
                
                String finalHypothesis = last != null ? last.fullResponse() : "Unable to complete reasoning";
                double finalConfidence = last != null ? last.confidence() : 0.0;
                
                return Mono.just(new ReasoningResult(
                    steps,
                    finalHypothesis,
                    finalConfidence,
                    StopReason.TIMEOUT
                ));
            });
    }
    
    private Mono<ReasoningResult> executeRound(
            PipelineContext context,
            DeepArtifactStore store,
            DeepPlan plan,
            String question,
            List<ChatMessage> history,
            int round,
            int maxRounds,
            Sinks.Many<SseEnvelope> eventSink) {
        
        log.debug("Executing reasoning round {}/{} for runId={}", round, maxRounds, context.runId());
        
        // Get previous reasoning summary
        String previousReasoning = getPreviousReasoningSummary(store);
        
        // Build prompt
        String prompt = String.format(
            REASONING_PROMPT_TEMPLATE,
            plan.objective(),
            String.join(", ", plan.subtasks()),
            round,
            maxRounds,
            previousReasoning,
            question
        );
        
        return llmService.streamResponse(prompt, history, "DEEP")
            .reduce(new StringBuilder(), StringBuilder::append)
            .map(StringBuilder::toString)
            .flatMap(response -> {
                // Parse response
                ReasoningStep step = parseReasoningStep(response, round);
                
                // Check progress BEFORE adding current step (compare to previous)
                boolean hasProgress = store.hasProgress(step.hypothesis());
                
                store.addReasoningStep(step);
                
                // Emit SSE event (safe summary, no raw CoT)
                emitStepEvent(context, step, round, maxRounds, eventSink);
                
                // Check stop conditions
                StopReason stopReason = checkStopConditions(store, step, round, maxRounds, hasProgress);
                
                if (stopReason != null) {
                    log.info("Reasoning stopped for runId={}: reason={}, round={}, confidence={}", 
                        context.runId(), stopReason, round, step.confidence());
                    
                    return Mono.just(new ReasoningResult(
                        store.getReasoningSteps(),
                        step.fullResponse(),
                        step.confidence(),
                        stopReason
                    ));
                }
                
                // Continue to next round
                return executeRound(context, store, plan, question, history, round + 1, maxRounds, eventSink);
            })
            .onErrorResume(e -> {
                log.error("Round {} failed for runId={}: {}", round, context.runId(), e.getMessage());
                
                // Create error step
                ReasoningStep errorStep = ReasoningStep.simple(round, "Error: " + e.getMessage(), 0.0);
                store.addReasoningStep(errorStep);
                
                List<ReasoningStep> steps = store.getReasoningSteps();
                ReasoningStep last = steps.size() > 1 ? steps.get(steps.size() - 2) : errorStep;
                
                return Mono.just(new ReasoningResult(
                    steps,
                    last.hypothesis(),
                    last.confidence(),
                    StopReason.ERROR
                ));
            });
    }
    
    private StopReason checkStopConditions(DeepArtifactStore store, ReasoningStep currentStep, int round, int maxRounds, boolean hasProgress) {
        // 1. Confidence threshold reached
        if (currentStep.confidence() >= config.getConfidenceThreshold()) {
            return StopReason.CONFIDENCE_REACHED;
        }
        
        // 2. Max rounds reached
        if (round >= maxRounds) {
            return StopReason.MAX_ROUNDS;
        }
        
        // 3. No progress (similar to previous hypothesis)
        if (!hasProgress) {
            return StopReason.NO_PROGRESS;
        }
        
        return null; // Continue
    }
    
    private ReasoningStep parseReasoningStep(String response, int round) {
        String hypothesis = extractGroup(HYPOTHESIS_PATTERN, response, "Reasoning in progress");
        double confidence = parseConfidence(extractGroup(CONFIDENCE_PATTERN, response, "0.5"));
        String evidenceStr = extractGroup(EVIDENCE_PATTERN, response, "");
        
        String preHypothesisContent = extractGroup(PRE_HYPOTHESIS_PATTERN, response, "").trim();
        String fullResponse;
        if (!preHypothesisContent.isEmpty()) {
            fullResponse = preHypothesisContent + "\n\n" + hypothesis.trim();
        } else {
            fullResponse = hypothesis.trim();
        }
        
        List<String> evidenceRefs = evidenceStr.isBlank() ? 
            List.of() : 
            List.of(evidenceStr.split(",")).stream().map(String::trim).toList();
        
        return new ReasoningStep(
            round,
            hypothesis.trim(),
            fullResponse,
            evidenceRefs,
            confidence,
            System.currentTimeMillis()
        );
    }
    
    private double parseConfidence(String value) {
        try {
            double conf = Double.parseDouble(value.trim());
            return Math.max(0.0, Math.min(1.0, conf)); // Clamp to [0,1]
        } catch (NumberFormatException e) {
            return 0.5; // Default
        }
    }
    
    private String extractGroup(Pattern pattern, String text, String defaultValue) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return defaultValue;
    }
    
    private String getPreviousReasoningSummary(DeepArtifactStore store) {
        List<ReasoningStep> steps = store.getReasoningSteps();
        if (steps.isEmpty()) {
            return "None (first round)";
        }
        
        // Summarize last step only to save tokens
        ReasoningStep last = steps.get(steps.size() - 1);
        return String.format("Round %d: %s (confidence: %.2f)", 
            last.round(), 
            truncate(last.hypothesis(), 200),
            last.confidence());
    }
    
    private void emitStepEvent(
            PipelineContext context, 
            ReasoningStep step, 
            int round, 
            int maxRounds,
            Sinks.Many<SseEnvelope> eventSink) {
        
        // Safe summary - no raw chain-of-thought
        SseEnvelope event = new SseEnvelope(
            StageNames.DEEP_REASONING_STEP,
            "Reasoning round " + round,
            Map.of(
                "round", round,
                "maxRounds", maxRounds,
                "confidence", step.confidence(),
                "status", step.confidence() >= config.getConfidenceThreshold() ? "confident" : "exploring"
            ),
            context.nextSeq(),
            System.currentTimeMillis(),
            context.traceId(),
            context.sessionId()
        );
        
        if (eventSink != null) {
            eventSink.tryEmitNext(event);
        }
    }
    
    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
    
    // ============ Sprint 4: Verification-triggered additional rounds ============
    
    /**
     * Check if an additional reasoning round should be executed.
     * This is called after verification and reflection stages.
     * 
     * @param context Pipeline context
     * @return true if additional round should be executed
     */
    public boolean shouldExecuteAdditionalRound(PipelineContext context) {
        boolean needsAdditional = context.needsAdditionalRound();
        int currentRound = context.getCurrentRound();
        int maxRounds = config.getMaxRoundsCap();
        
        if (!needsAdditional) {
            log.debug("No additional round needed for runId={}", context.runId());
            return false;
        }
        
        if (currentRound >= maxRounds) {
            log.info("Max rounds ({}) reached for runId={}, no additional round", maxRounds, context.runId());
            return false;
        }
        
        log.info("Additional round needed for runId={}: round {} of {}", 
            context.runId(), currentRound + 1, maxRounds);
        return true;
    }
    
    /**
     * Prepare context for an additional reasoning round.
     * 
     * @param context Pipeline context
     */
    public void prepareAdditionalRound(PipelineContext context) {
        int nextRound = context.getCurrentRound() + 1;
        context.setCurrentRound(nextRound);
        context.setNeedsAdditionalRound(false); // Reset flag
        
        log.debug("Prepared round {} for runId={}", nextRound, context.runId());
    }
    
    /**
     * Get the effective max rounds considering both policy and config cap.
     * 
     * @param context Pipeline context
     * @return effective max rounds
     */
    public int getEffectiveMaxRounds(PipelineContext context) {
        int policyMax = context.policy() != null ? context.policy().maxToolRounds() : config.getMaxRoundsCap();
        return Math.min(policyMax, config.getMaxRoundsCap());
    }
}
