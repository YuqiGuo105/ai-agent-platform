package com.mrpot.agent.service.pipeline;

import java.time.Duration;
import java.util.function.Predicate;

/**
 * Configuration for a pipeline stage.
 * Controls timeout, SSE event emission, and execution conditions.
 *
 * @param timeout        stage timeout duration
 * @param emitSseEvent   whether to emit SSE events for this stage
 * @param emitErrorEvent whether to emit error events on stage failure
 * @param condition      predicate to determine if stage should execute
 */
public record StageConfig(
    Duration timeout,
    boolean emitSseEvent,
    boolean emitErrorEvent,
    Predicate<PipelineContext> condition
) {
    
    // Default configuration values
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    
    /**
     * Default stage configuration.
     * 30s timeout, emit both SSE and error events, always execute.
     */
    public static final StageConfig DEFAULT = new StageConfig(
        DEFAULT_TIMEOUT,
        true,
        true,
        ctx -> true
    );
    
    /**
     * Create a stage config with custom timeout.
     *
     * @param timeout the timeout duration
     * @return stage config with custom timeout
     */
    public static StageConfig of(Duration timeout) {
        return new StageConfig(
            timeout,
            true,
            true,
            ctx -> true
        );
    }
    
    /**
     * Create a stage config with conditional execution.
     *
     * @param condition the execution condition
     * @return stage config with conditional execution
     */
    public static StageConfig conditional(Predicate<PipelineContext> condition) {
        return new StageConfig(
            DEFAULT_TIMEOUT,
            true,
            true,
            condition
        );
    }
    
    /**
     * Create a stage config with timeout and condition.
     *
     * @param timeout   the timeout duration
     * @param condition the execution condition
     * @return stage config with both settings
     */
    public static StageConfig of(Duration timeout, Predicate<PipelineContext> condition) {
        return new StageConfig(
            timeout,
            true,
            true,
            condition
        );
    }
    
    /**
     * Create a silent stage config (no SSE events).
     *
     * @return stage config without SSE emission
     */
    public static StageConfig silent() {
        return new StageConfig(
            DEFAULT_TIMEOUT,
            false,
            false,
            ctx -> true
        );
    }
    
    /**
     * Create a stage config where errors are suppressed from SSE.
     *
     * @return stage config without error emission
     */
    public static StageConfig suppressErrors() {
        return new StageConfig(
            DEFAULT_TIMEOUT,
            true,
            false,
            ctx -> true
        );
    }
    
    /**
     * Check if this stage should execute given the context.
     *
     * @param context the pipeline context
     * @return true if the stage should execute
     */
    public boolean shouldExecute(PipelineContext context) {
        return condition.test(context);
    }
    
    /**
     * Create a new config with a different timeout.
     *
     * @param timeout the new timeout
     * @return new stage config
     */
    public StageConfig withTimeout(Duration timeout) {
        return new StageConfig(timeout, emitSseEvent, emitErrorEvent, condition);
    }
    
    /**
     * Create a new config with a different condition.
     *
     * @param condition the new condition
     * @return new stage config
     */
    public StageConfig withCondition(Predicate<PipelineContext> condition) {
        return new StageConfig(timeout, emitSseEvent, emitErrorEvent, condition);
    }
    
    /**
     * Create a new config with SSE emission toggled.
     *
     * @param emit whether to emit SSE events
     * @return new stage config
     */
    public StageConfig withSseEmit(boolean emit) {
        return new StageConfig(timeout, emit, emitErrorEvent, condition);
    }
    
    /**
     * Create a new config with error emission toggled.
     *
     * @param emit whether to emit error events
     * @return new stage config
     */
    public StageConfig withErrorEmit(boolean emit) {
        return new StageConfig(timeout, emitSseEvent, emit, condition);
    }
}
