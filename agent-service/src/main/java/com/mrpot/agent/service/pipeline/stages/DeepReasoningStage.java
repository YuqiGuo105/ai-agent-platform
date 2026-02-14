package com.mrpot.agent.service.pipeline.stages;

import com.mrpot.agent.common.policy.ExecutionPolicy;
import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.model.ChatMessage;
import com.mrpot.agent.service.pipeline.DeepArtifactStore;
import com.mrpot.agent.service.pipeline.DeepReasoningCoordinator;
import com.mrpot.agent.service.pipeline.DeepReasoningCoordinator.ReasoningResult;
import com.mrpot.agent.service.pipeline.PipelineContext;
import com.mrpot.agent.service.pipeline.Processor;
import com.mrpot.agent.service.pipeline.artifacts.DeepPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Map;

/**
 * Deep reasoning stage - performs multi-round reasoning based on the generated plan.
 * 
 * <p>This stage delegates to {@link DeepReasoningCoordinator} for bounded micro-iterations
 * and emits DEEP_REASONING_START and DEEP_REASONING_DONE SSE events.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeepReasoningStage implements Processor<Void, SseEnvelope> {
    
    private final DeepReasoningCoordinator coordinator;
    
    @Override
    public Mono<SseEnvelope> process(Void input, PipelineContext context) {
        log.debug("Starting deep reasoning stage for runId={}", context.runId());
        
        DeepArtifactStore store = new DeepArtifactStore(context);
        DeepPlan plan = store.getPlan();
        
        if (plan == null) {
            log.warn("No plan found in context for runId={}, using fallback plan", context.runId());
            String question = getQuestion(context);
            plan = DeepPlan.fallback(question != null ? question : "");
            store.setPlan(plan);
        }
        
        // Get question and history from context
        String question = getQuestion(context);
        List<ChatMessage> history = getHistory(context);
        ExecutionPolicy policy = getPolicy(context);
        
        if (history == null) {
            history = List.of();
        }
        
        // Create event sink for intermediate events (used by coordinator)
        Sinks.Many<SseEnvelope> eventSink = Sinks.many().unicast().onBackpressureBuffer();
        
        final DeepPlan finalPlan = plan;
        final List<ChatMessage> finalHistory = history;
        
        // Execute reasoning via coordinator and emit DEEP_REASONING_DONE
        return coordinator.executeReasoning(
                context, 
                finalPlan, 
                question, 
                finalHistory, 
                policy, 
                eventSink
            )
            .map(result -> createDoneEnvelope(context, result))
            .doOnSuccess(done -> {
                // Store final reasoning for synthesis stage
                ReasoningResult result = buildResultFromStore(store);
                String lastFullResponse = "";
                if (result != null) {
                    var steps = result.steps();
                    if (steps != null && !steps.isEmpty()) {
                        lastFullResponse = steps.get(steps.size() - 1).fullResponse();
                    }
                }
                context.setDeepReasoning(Map.of(
                    "rounds", store.getReasoningStepCount(),
                    "hypothesis", result != null ? result.finalHypothesis() : "Complete",
                    "fullResponse", lastFullResponse != null ? lastFullResponse : "",
                    "confidence", result != null ? result.finalConfidence() : 0.5,
                    "status", "complete"
                ));
                log.info("Deep reasoning completed for runId={}: {} round(s)", 
                    context.runId(), store.getReasoningStepCount());
            })
            .onErrorResume(e -> {
                log.error("Failed to complete deep reasoning for runId={}: {}", 
                    context.runId(), e.getMessage(), e);
                
                context.setDeepReasoning(Map.of(
                    "rounds", store.getReasoningStepCount(),
                    "status", "error",
                    "error", e.getMessage() != null ? e.getMessage() : "Unknown error"
                ));
                
                return Mono.just(new SseEnvelope(
                    StageNames.DEEP_REASONING_DONE,
                    "Reasoning fallback",
                    Map.of(
                        "status", "error",
                        "rounds", store.getReasoningStepCount(),
                        "error", e.getMessage() != null ? e.getMessage() : "Unknown error"
                    ),
                    context.nextSeq(),
                    System.currentTimeMillis(),
                    context.traceId(),
                    context.sessionId()
                ));
            });
    }
    
    private SseEnvelope createDoneEnvelope(PipelineContext context, ReasoningResult result) {
        return new SseEnvelope(
            StageNames.DEEP_REASONING_DONE,
            "Reasoning done (" + result.steps().size() + " steps)",
            Map.of(
                "status", "complete",
                "rounds", result.steps().size(),
                "confidence", result.finalConfidence(),
                "stopReason", result.stopReason().name()
            ),
            context.nextSeq(),
            System.currentTimeMillis(),
            context.traceId(),
            context.sessionId()
        );
    }
    
    private ReasoningResult buildResultFromStore(DeepArtifactStore store) {
        var steps = store.getReasoningSteps();
        if (steps.isEmpty()) {
            return null;
        }
        var last = steps.get(steps.size() - 1);
        return new ReasoningResult(
            steps,
            last.fullResponse(),
            last.confidence(),
            DeepReasoningCoordinator.StopReason.MAX_ROUNDS
        );
    }
    
    /**
     * Get user question from context request or working memory.
     */
    private String getQuestion(PipelineContext context) {
        if (context.request() != null && context.request().question() != null) {
            return context.request().question();
        }
        return context.get(PipelineContext.KEY_USER_QUESTION);
    }
    
    /**
     * Get conversation history from working memory.
     */
    private List<ChatMessage> getHistory(PipelineContext context) {
        return context.get(PipelineContext.KEY_HISTORY);
    }
    
    /**
     * Get execution policy from context or working memory.
     */
    private ExecutionPolicy getPolicy(PipelineContext context) {
        if (context.policy() != null) {
            return context.policy();
        }
        return context.get(PipelineContext.KEY_POLICY);
    }
}
