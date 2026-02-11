package com.mrpot.agent.service.policy;

import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.policy.ExecutionPolicy;
import com.mrpot.agent.common.policy.ToolAccessLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Builds execution policies based on scope mode.
 * Maps scope modes to specific policy configurations that control
 * file extraction, RAG, tool access, and execution behavior.
 */
@Slf4j
@Component
public class PolicyBuilder {
    
    /**
     * Build an execution policy based on the given scope mode.
     *
     * @param scopeMode the scope mode to build policy for
     * @return the execution policy
     */
    public ExecutionPolicy build(ScopeMode scopeMode) {
        log.debug("Building execution policy for scope mode: {}", scopeMode);
        
        return switch (scopeMode) {
            case OWNER_ONLY -> buildOwnerPolicy();
            case PRIVACY_SAFE -> buildPrivacySafePolicy();
            case PRIVACY_REQUEST -> buildPrivacyRequestPolicy();
            case GENERAL, AUTO -> buildGeneralPolicy();
        };
    }
    
    /**
     * Build policy for OWNER_ONLY scope.
     * Full access to all features with higher limits.
     *
     * @return owner execution policy
     */
    private ExecutionPolicy buildOwnerPolicy() {
        log.debug("Building OWNER policy with full access");
        return ExecutionPolicy.builder()
            .allowFile(true)
            .allowRag(true)
            .toolAccessLevel(ToolAccessLevel.FULL)
            .maxToolRounds(5)
            .allowSideEffectTools(false)  // Still restrict side effects by default
            .preferredMode("DEEP")
            .build();
    }
    
    /**
     * Build policy for GENERAL scope.
     * Standard access with limited tools and no RAG.
     *
     * @return general execution policy
     */
    private ExecutionPolicy buildGeneralPolicy() {
        log.debug("Building GENERAL policy with standard access");
        return ExecutionPolicy.builder()
            .allowFile(true)
            .allowRag(false)
            .toolAccessLevel(ToolAccessLevel.TIER_A_B)
            .maxToolRounds(2)
            .allowSideEffectTools(false)
            .preferredMode("FAST")
            .build();
    }
    
    /**
     * Build policy for PRIVACY_SAFE scope.
     * Restricted access with minimal features.
     *
     * @return privacy safe execution policy
     */
    private ExecutionPolicy buildPrivacySafePolicy() {
        log.debug("Building PRIVACY_SAFE policy with restricted access");
        return ExecutionPolicy.builder()
            .allowFile(false)
            .allowRag(false)
            .toolAccessLevel(ToolAccessLevel.TIER_A)
            .maxToolRounds(1)
            .allowSideEffectTools(false)
            .preferredMode("FAST")
            .build();
    }
    
    /**
     * Build policy for PRIVACY_REQUEST scope.
     * Similar to PRIVACY_SAFE but for explicit privacy requests.
     *
     * @return privacy request execution policy
     */
    private ExecutionPolicy buildPrivacyRequestPolicy() {
        log.debug("Building PRIVACY_REQUEST policy");
        return ExecutionPolicy.builder()
            .allowFile(false)
            .allowRag(false)
            .toolAccessLevel(ToolAccessLevel.NONE)
            .maxToolRounds(0)
            .allowSideEffectTools(false)
            .preferredMode("FAST")
            .build();
    }
}
