package com.mrpot.agent.service.pipeline.stages;

import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.service.pipeline.PipelineContext;
import com.mrpot.agent.service.pipeline.Processor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Deep reasoning stage - performs reasoning based on the generated plan.
 * 
 * This stage executes reasoning logic and produces a reasoning summary.
 * Current implementation is a minimal 1-step version that does not involve LLM calls.
 * 
 * Future versions may include:
 * - Multi-step reasoning with iterative refinement
 * - LLM-based reasoning with chain-of-thought
 * - Tool calling and external knowledge retrieval
 * - Reasoning validation and self-correction
 */
@Slf4j
public class DeepReasoningStage implements Processor<Void, SseEnvelope> {
    
    @Override
    public Mono<SseEnvelope> process(Void input, PipelineContext context) {
        log.debug("Starting deep reasoning stage for runId={}", context.runId());
        
        return Mono.fromSupplier(() -> {
            // Get the plan from context (created by DeepPlanStage)
            Map<String, Object> plan = context.getDeepPlan();
            
            if (plan == null) {
                log.warn("No plan found in context for runId={}, using default reasoning", 
                    context.runId());
            }
            
            // Create reasoning result DTO
            Map<String, Object> reasoningDto = Map.of(
                "round", 1,
                "status", "complete",
                "summary", "Reasoning complete",
                "reasoningSteps", 1,
                "completedAt", System.currentTimeMillis()
            );
            
            // Store reasoning result in context for synthesis stage
            context.setDeepReasoning(reasoningDto);
            
            log.info("Deep reasoning completed for runId={}: {} step(s)", 
                context.runId(), 
                reasoningDto.get("reasoningSteps"));
            
            // Create SSE envelope with reasoning result
            return new SseEnvelope(
                StageNames.DEEP_REASONING,
                "Reasoning complete",
                Map.of(
                    "round", 1,
                    "status", "complete",
                    "summary", "Reasoning complete"
                ),
                context.nextSeq(),
                System.currentTimeMillis(),
                context.traceId(),
                context.sessionId()
            );
        }).onErrorResume(e -> {
            log.error("Failed to complete deep reasoning for runId={}: {}", 
                context.runId(), e.getMessage(), e);
            
            // Store error state in context
            context.setDeepReasoning(Map.of(
                "round", 1,
                "status", "error",
                "summary", "Reasoning failed: " + (e.getMessage() != null ? e.getMessage() : "Unknown error")
            ));
            
            // Return error indicator envelope
            return Mono.just(new SseEnvelope(
                StageNames.DEEP_REASONING,
                "Reasoning failed",
                Map.of(
                    "round", 1,
                    "status", "error",
                    "summary", "Reasoning failed: " + (e.getMessage() != null ? e.getMessage() : "Unknown error")
                ),
                context.nextSeq(),
                System.currentTimeMillis(),
                context.traceId(),
                context.sessionId()
            ));
        });
    }
}
