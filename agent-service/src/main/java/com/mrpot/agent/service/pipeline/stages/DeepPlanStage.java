package com.mrpot.agent.service.pipeline.stages;

import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.service.pipeline.PipelineContext;
import com.mrpot.agent.service.pipeline.Processor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Deep plan stage - creates a planning structure for deep reasoning.
 * 
 * This stage generates a simple plan DTO and stores it in the pipeline context
 * for subsequent reasoning stages to use.
 * 
 * Current implementation is a minimal version that does not involve LLM calls.
 * Future versions may include:
 * - LLM-based plan generation
 * - Dynamic step creation based on question complexity
 * - Plan validation and refinement
 */
@Slf4j
public class DeepPlanStage implements Processor<Void, SseEnvelope> {
    
    @Override
    public Mono<SseEnvelope> process(Void input, PipelineContext context) {
        log.debug("Starting deep plan stage for runId={}", context.runId());
        
        return Mono.fromSupplier(() -> {
            // Create a simple plan DTO
            Map<String, Object> planDto = Map.of(
                "steps", List.of("analyze_problem", "reason"),
                "status", "created",
                "createdAt", System.currentTimeMillis()
            );
            
            // Store plan in context for subsequent stages
            context.setDeepPlan(planDto);
            
            log.info("Deep plan created for runId={}: {} steps", 
                context.runId(), 
                ((List<?>) planDto.get("steps")).size());
            
            // Create SSE envelope with plan result
            return new SseEnvelope(
                StageNames.DEEP_PLAN,
                "Plan created",
                Map.of(
                    "round", 1,
                    "status", "complete",
                    "summary", "Plan created with " + ((List<?>) planDto.get("steps")).size() + " steps"
                ),
                context.nextSeq(),
                System.currentTimeMillis(),
                context.traceId(),
                context.sessionId()
            );
        }).onErrorResume(e -> {
            log.error("Failed to create deep plan for runId={}: {}", 
                context.runId(), e.getMessage(), e);
            
            // Store empty plan on error
            context.setDeepPlan(Map.of("steps", List.of(), "status", "error"));
            
            // Return error indicator envelope
            return Mono.just(new SseEnvelope(
                StageNames.DEEP_PLAN,
                "Plan failed",
                Map.of(
                    "round", 1,
                    "status", "error",
                    "summary", "Failed to create plan: " + (e.getMessage() != null ? e.getMessage() : "Unknown error")
                ),
                context.nextSeq(),
                System.currentTimeMillis(),
                context.traceId(),
                context.sessionId()
            ));
        });
    }
}
