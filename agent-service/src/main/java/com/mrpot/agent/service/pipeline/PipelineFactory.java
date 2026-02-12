package com.mrpot.agent.service.pipeline;

import com.mrpot.agent.common.api.RagAnswerRequest;
import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.policy.ExecutionPolicy;
import com.mrpot.agent.service.policy.ModeDecider;
import com.mrpot.agent.service.policy.PolicyBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Pipeline factory service - responsible for creating and selecting pipeline execution strategies.
 * 
 * Main responsibilities:
 * 1. Select appropriate pipeline type (FAST or DEEP) based on request and policy
 * 2. Create PipelineContext for pipeline execution
 * 3. Build execution policy
 * 
 * Usage example:
 * <pre>
 * PipelineContext ctx = pipelineFactory.createContext(request, traceId);
 * PipelineRunner pipeline = pipelineFactory.createPipeline(ctx);
 * Flux<SseEnvelope> events = pipeline.run(ctx);
 * </pre>
 */
@Slf4j
@Service
public class PipelineFactory {
    
    private static final String MODE_FAST = "FAST";
    private static final String MODE_DEEP = "DEEP";
    private static final String DEFAULT_USER_ID = "userId_placeholder";
    
    private final FastPipeline fastPipeline;
    private final DeepPipeline deepPipeline;
    private final PolicyBuilder policyBuilder;
    private final ModeDecider modeDecider;
    
    public PipelineFactory(
        FastPipeline fastPipeline,
        DeepPipeline deepPipeline,
        PolicyBuilder policyBuilder,
        ModeDecider modeDecider
    ) {
        this.fastPipeline = fastPipeline;
        this.deepPipeline = deepPipeline;
        this.policyBuilder = policyBuilder;
        this.modeDecider = modeDecider;
    }
    
    /**
     * Create pipeline context.
     * 
     * Build execution policy based on request, determine execution mode,
     * and create context object containing all necessary information.
     *
     * @param request original request
     * @param traceId distributed tracing ID
     * @return pipeline context
     */
    public PipelineContext createContext(RagAnswerRequest request, String traceId) {
        String runId = UUID.randomUUID().toString();
        String sessionId = request.sessionId();
        
        // Determine scope mode
        ScopeMode scopeMode = resolveScopeMode(request);
        
        // Build execution policy
        ExecutionPolicy policy = policyBuilder.build(scopeMode);
        
        // Determine execution mode
        String executionMode = modeDecider.decide(request, policy);
        
        log.info("Creating pipeline context: runId={}, traceId={}, scopeMode={}, executionMode={}",
            runId, traceId, scopeMode, executionMode);
        
        return PipelineContext.builder()
            .runId(runId)
            .traceId(traceId)
            .sessionId(sessionId)
            .userId(DEFAULT_USER_ID)
            .request(request)
            .scopeMode(scopeMode)
            .policy(policy)
            .executionMode(executionMode)
            .build();
    }
    
    /**
     * Create pipeline runner based on context.
     * 
     * Currently supports FAST mode, DEEP mode falls back to FAST pipeline.
     * Use switch for future extensibility to support additional pipeline modes.
     *
     * @param context pipeline context
     * @return configured pipeline runner
     */
    public PipelineRunner createPipeline(PipelineContext context) {
        String mode = context.executionMode();
        
        log.debug("Creating pipeline for mode: {}", mode);
        
        // Use switch for future extensibility (e.g., adding DEEP, CUSTOM modes)
        return switch (mode) {
            case MODE_DEEP -> {
                log.info("DEEP mode requested, using DEEP pipeline");
                yield deepPipeline.build();
            }
            case MODE_FAST -> fastPipeline.build();
            default -> {
                log.warn("Unknown execution mode: {}, defaulting to FAST", mode);
                yield fastPipeline.build();
            }
        };
    }
    
    /**
     * Convenience method: create context and pipeline in one call.
     *
     * @param request original request
     * @param traceId distributed tracing ID
     * @return result object containing both context and pipeline
     */
    public PipelineCreationResult create(RagAnswerRequest request, String traceId) {
        PipelineContext context = createContext(request, traceId);
        PipelineRunner pipeline = createPipeline(context);
        return new PipelineCreationResult(context, pipeline);
    }
    
    /**
     * Resolve scope mode, handling null and AUTO cases.
     */
    private ScopeMode resolveScopeMode(RagAnswerRequest request) {
        ScopeMode mode = request.scopeMode();
        if (mode == null || mode == ScopeMode.AUTO) {
            return ScopeMode.GENERAL;
        }
        return mode;
    }
    
    /**
     * Pipeline creation result containing context and pipeline runner.
     */
    public record PipelineCreationResult(
        PipelineContext context,
        PipelineRunner pipeline
    ) {}
}
