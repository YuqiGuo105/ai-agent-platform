package com.mrpot.agent.service.pipeline;

import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.sse.StageNames;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Executes a sequence of pipeline stages with error handling and SSE event emission.
 * Provides timeout control, conditional execution, and graceful degradation.
 */
@Slf4j
public class PipelineRunner {
    
    private final List<PipelineStage<?>> stages;
    private Duration globalTimeout;
    
    /**
     * Internal record representing a pipeline stage.
     */
    private record PipelineStage<T>(
        String name,
        Processor<?, T> processor,
        StageConfig config
    ) {}
    
    /**
     * Create a new pipeline runner.
     */
    public PipelineRunner() {
        this.stages = new ArrayList<>();
        this.globalTimeout = Duration.ofSeconds(60);
    }
    
    /**
     * Add a stage to the pipeline.
     *
     * @param stageName the name of the stage
     * @param processor the processor to execute
     * @param config    the stage configuration
     * @param <T>       the output type of the processor
     * @return this runner for chaining
     */
    public <T> PipelineRunner addStage(String stageName, Processor<?, T> processor, StageConfig config) {
        stages.add(new PipelineStage<>(stageName, processor, config));
        return this;
    }
    
    /**
     * Add a stage with default configuration.
     *
     * @param stageName the name of the stage
     * @param processor the processor to execute
     * @param <T>       the output type of the processor
     * @return this runner for chaining
     */
    public <T> PipelineRunner addStage(String stageName, Processor<?, T> processor) {
        return addStage(stageName, processor, StageConfig.DEFAULT);
    }
    
    /**
     * Set the global timeout for the entire pipeline.
     *
     * @param timeout the global timeout duration
     * @return this runner for chaining
     */
    public PipelineRunner withGlobalTimeout(Duration timeout) {
        this.globalTimeout = timeout;
        return this;
    }
    
    /**
     * Execute the pipeline and return a flux of SSE events.
     *
     * @param context the pipeline context
     * @return Flux of SSE envelopes
     */
    public Flux<SseEnvelope> run(PipelineContext context) {
        log.info("Starting pipeline run: runId={}, executionMode={}", 
            context.runId(), context.executionMode());
        
        // Emit START event
        Flux<SseEnvelope> startEvent = Flux.just(createEnvelope(
            StageNames.START,
            "Starting " + context.executionMode() + " pipeline",
            Map.of(
                "runId", context.runId(),
                "mode", context.executionMode(),
                "scopeMode", context.scopeMode().name()
            ),
            context
        ));
        
        // Execute all stages
        Flux<SseEnvelope> stageEvents = executeStages(context);
        
        // Emit ANSWER_FINAL event
        Flux<SseEnvelope> finalEvent = Flux.defer(() -> {
            long totalLatencyMs = context.elapsedMs();
            log.info("Pipeline completed: runId={}, totalLatencyMs={}", 
                context.runId(), totalLatencyMs);
            
            return Flux.just(createEnvelope(
                StageNames.ANSWER_FINAL,
                "Complete",
                Map.of(
                    "answer", context.getFinalAnswer(),
                    "totalLatencyMs", totalLatencyMs
                ),
                context
            ));
        });
        
        return startEvent
            .concatWith(stageEvents)
            .concatWith(finalEvent)
            .timeout(globalTimeout)
            .onErrorResume(e -> {
                log.error("Pipeline error: runId={}, error={}", context.runId(), e.getMessage(), e);
                return Flux.just(createEnvelope(
                    StageNames.ERROR,
                    "Pipeline error: " + e.getMessage(),
                    Map.of(
                        "error", e.getClass().getSimpleName(),
                        "message", e.getMessage() != null ? e.getMessage() : "Unknown error"
                    ),
                    context
                )).concatWith(Flux.just(createEnvelope(
                    StageNames.ANSWER_FINAL,
                    "Complete with errors",
                    Map.of(
                        "answer", context.getFinalAnswer(),
                        "totalLatencyMs", context.elapsedMs(),
                        "hasError", true
                    ),
                    context
                )));
            });
    }
    
    /**
     * Execute all stages sequentially.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Flux<SseEnvelope> executeStages(PipelineContext context) {
        Flux<SseEnvelope> result = Flux.empty();
        
        for (PipelineStage<?> stage : stages) {
            result = result.concatWith(executeStage(stage, context));
        }
        
        return result;
    }
    
    /**
     * Execute a single stage with error handling.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Flux<SseEnvelope> executeStage(PipelineStage<?> stage, PipelineContext context) {
        // Check if stage should execute
        if (!stage.config().shouldExecute(context)) {
            log.debug("Skipping stage {}: condition not met", stage.name());
            return Flux.empty();
        }
        
        log.debug("Executing stage: {}", stage.name());
        long stageStartMs = System.currentTimeMillis();
        
        return Mono.defer(() -> {
            try {
                Processor processor = stage.processor();
                return processor.process(null, context);
            } catch (Exception e) {
                return Mono.error(e);
            }
        })
        .timeout(stage.config().timeout())
        .flatMapMany(output -> {
            // Store output in working memory
            context.put(stage.name() + ".output", output);
            
            long stageLatencyMs = System.currentTimeMillis() - stageStartMs;
            log.debug("Stage {} completed in {}ms", stage.name(), stageLatencyMs);
            
            // Handle different output types
            if (output instanceof Flux) {
                return (Flux<SseEnvelope>) output;
            } else if (output instanceof SseEnvelope) {
                return Flux.just((SseEnvelope) output);
            } else if (output != null) {
                // Wrap non-envelope output
                if (stage.config().emitSseEvent()) {
                    return Flux.just(createEnvelope(
                        stage.name(),
                        "Stage " + stage.name() + " complete",
                        output,
                        context
                    ));
                }
            }
            return Flux.empty();
        })
        .onErrorResume(err -> {
            Throwable e = (Throwable) err;
            long stageLatencyMs = System.currentTimeMillis() - stageStartMs;
            log.warn("Stage {} failed after {}ms: {}", stage.name(), stageLatencyMs, e.getMessage());
            
            // Emit error event if configured
            if (stage.config().emitErrorEvent()) {
                return Flux.just(createEnvelope(
                    StageNames.ERROR,
                    "Stage " + stage.name() + " failed: " + e.getMessage(),
                    Map.of(
                        "stage", stage.name(),
                        "error", e.getClass().getSimpleName(),
                        "message", e.getMessage() != null ? e.getMessage() : "Unknown error"
                    ),
                    context
                ));
            }
            
            // Graceful degradation - continue pipeline
            return Flux.empty();
        });
    }
    
    /**
     * Create an SSE envelope.
     */
    private SseEnvelope createEnvelope(
        String stage,
        String message,
        Object payload,
        PipelineContext context
    ) {
        return new SseEnvelope(
            stage,
            message,
            payload,
            context.nextSeq(),
            System.currentTimeMillis(),
            context.traceId(),
            context.sessionId()
        );
    }
}
