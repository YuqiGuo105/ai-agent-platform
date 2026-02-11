package com.mrpot.agent.service.policy;

import com.mrpot.agent.common.api.RagAnswerRequest;
import com.mrpot.agent.common.api.ScopeMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Determines the scope mode for a request based on request parameters and user permissions.
 * The scope mode affects what operations and tools are available during request processing.
 */
@Slf4j
@Component
public class ScopeClassifier {
    
    /**
     * Classify the request to determine the appropriate scope mode.
     * Priority:
     * 1. Explicit scopeMode from request (if not AUTO)
     * 2. Check if user is owner (validate OWNER_ONLY permission)
     * 3. Default to GENERAL
     *
     * @param request the RAG answer request
     * @param userId  the user ID making the request
     * @return the determined scope mode
     */
    public ScopeMode classify(RagAnswerRequest request, String userId) {
        // If request explicitly specifies a scope mode (not AUTO), use it
        ScopeMode requestedMode = request.resolveScopeMode();
        if (requestedMode != ScopeMode.AUTO) {
            // Validate that user has permission for the requested mode
            if (requestedMode == ScopeMode.OWNER_ONLY && !isOwner(userId)) {
                log.warn("User {} requested OWNER_ONLY mode but is not owner, falling back to GENERAL", userId);
                return ScopeMode.GENERAL;
            }
            log.debug("Using explicitly requested scope mode: {}", requestedMode);
            return requestedMode;
        }
        
        // Check if user is owner
        if (isOwner(userId)) {
            log.debug("User {} is owner, using OWNER_ONLY scope mode", userId);
            return ScopeMode.OWNER_ONLY;
        }
        
        // Default to GENERAL
        log.debug("User {} using default GENERAL scope mode", userId);
        return ScopeMode.GENERAL;
    }
    
    /**
     * Check if the user is an owner.
     * 
     * TODO: Integrate with actual permission system for proper authorization.
     * Currently uses hardcoded values for development.
     *
     * @param userId the user ID to check
     * @return true if user is owner
     */
    private boolean isOwner(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        // TODO: Replace with permission system integration
        // Currently hardcoded for development
        return "yuqi".equalsIgnoreCase(userId.trim()) || "owner".equalsIgnoreCase(userId.trim());
    }
}
