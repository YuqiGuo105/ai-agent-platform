package com.mrpot.agent.service.policy;

import com.mrpot.agent.common.api.RagAnswerRequest;
import com.mrpot.agent.common.policy.ExecutionPolicy;
import com.mrpot.agent.service.pipeline.PipelineContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Decides the execution mode (FAST or DEEP) based on request and policy.
 * 
 * Simple decision logic:
 * 1. Explicit DEEPTHINKING request + policy allows (maxToolRounds >= 3) → DEEP
 * 2. Otherwise → FAST
 */
@Slf4j
@Component
public class ModeDecider {
    
    private static final String MODE_FAST = "FAST";
    private static final String MODE_DEEP = "DEEP";
    private static final String REQUEST_MODE_DEEPTHINKING = "DEEPTHINKING";
    private static final int MIN_TOOL_ROUNDS_FOR_DEEP = 3;
    
    /**
     * Decide the execution mode based on request and policy.
     *
     * @param request the RAG answer request
     * @param policy  the execution policy
     * @return "FAST" or "DEEP" execution mode
     */
    public String decide(RagAnswerRequest request, ExecutionPolicy policy) {
        return decide(request, policy, null);
    }
    
    /**
     * Decide the execution mode with optional context.
     *
     * @param request the RAG answer request
     * @param policy  the execution policy
     * @param context the pipeline context (unused, kept for API compatibility)
     * @return "FAST" or "DEEP" execution mode
     */
    public String decide(RagAnswerRequest request, ExecutionPolicy policy, PipelineContext context) {
        String requestMode = request.resolveMode();
        
        // Explicit DEEPTHINKING request + policy allows → DEEP
        if (REQUEST_MODE_DEEPTHINKING.equalsIgnoreCase(requestMode) 
                && policy.maxToolRounds() >= MIN_TOOL_ROUNDS_FOR_DEEP) {
            log.info("Mode decision: DEEP (explicit DEEPTHINKING, maxToolRounds={})", 
                policy.maxToolRounds());
            return MODE_DEEP;
        }
        
        // Otherwise FAST
        log.info("Mode decision: FAST (mode={}, maxToolRounds={})", requestMode, policy.maxToolRounds());
        return MODE_FAST;
    }
}
