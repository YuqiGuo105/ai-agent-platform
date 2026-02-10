package com.mrpot.agent.common.telemetry;

import com.mrpot.agent.common.api.ScopeMode;

/**
 * Context for a single run/request.
 * Passed through tool calls to maintain tracing context.
 */
public record RunContext(
    String runId,          // unique ID for this run (UUID)
    String traceId,        // distributed tracing ID
    String sessionId,      // user session ID
    String userId,         // user ID
    ScopeMode scopeMode    // GENERAL, OWNER, ADMIN, etc.
) {
    /**
     * Create a new RunContext with generated runId.
     */
    public static RunContext create(String traceId, String sessionId, String userId, ScopeMode scopeMode) {
        return new RunContext(
            java.util.UUID.randomUUID().toString(),
            traceId,
            sessionId,
            userId,
            scopeMode
        );
    }
    
    /**
     * Create a RunContext from existing values.
     */
    public static RunContext of(String runId, String traceId, String sessionId, String userId, ScopeMode scopeMode) {
        return new RunContext(runId, traceId, sessionId, userId, scopeMode);
    }
}
