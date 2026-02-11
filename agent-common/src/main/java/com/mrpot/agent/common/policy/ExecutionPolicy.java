package com.mrpot.agent.common.policy;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;

/**
 * Execution policy that controls what operations are allowed during request processing.
 * Policies are determined based on scope mode and user permissions.
 *
 * @param allowFile          whether file extraction is allowed
 * @param allowRag           whether RAG retrieval is allowed
 * @param toolAccessLevel    tool access level (NONE, TIER_A, TIER_A_B, FULL)
 * @param maxToolRounds      maximum tool execution rounds
 * @param allowSideEffectTools whether side-effect tools are allowed
 * @param preferredMode      preferred execution mode ("FAST" or "DEEP")
 */
@Schema(description = "Execution policy that controls allowed operations")
public record ExecutionPolicy(
    
    @Schema(description = "Whether file extraction is allowed", example = "true")
    boolean allowFile,
    
    @Schema(description = "Whether RAG retrieval is allowed", example = "true")
    boolean allowRag,
    
    @Schema(description = "Tool access level", example = "TIER_A_B")
    ToolAccessLevel toolAccessLevel,
    
    @Schema(description = "Maximum tool execution rounds", example = "3")
    int maxToolRounds,
    
    @Schema(description = "Whether side-effect tools are allowed", example = "false")
    boolean allowSideEffectTools,
    
    @Schema(description = "Preferred execution mode (FAST or DEEP)", example = "FAST")
    String preferredMode
) {
    
    // Tier A tools: basic system utilities
    private static final Set<String> TIER_A_TOOLS = Set.of(
        "system.ping",
        "system.time"
    );
    
    // Tier B tools: read-only tools (extend as needed)
    private static final Set<String> TIER_B_TOOLS = Set.of(
        "kb.search",
        "file.understandUrl",
        "redis.get",
        "http.get"
    );
    
    /**
     * Check if tools can be used at all.
     *
     * @return true if toolAccessLevel is not NONE
     */
    public boolean canUseTools() {
        return toolAccessLevel != ToolAccessLevel.NONE;
    }
    
    /**
     * Check if a specific tool can be used based on policy.
     *
     * @param toolName      the name of the tool
     * @param hasSideEffect whether the tool has side effects
     * @return true if the tool is allowed
     */
    public boolean canUseTool(String toolName, boolean hasSideEffect) {
        if (toolAccessLevel == ToolAccessLevel.NONE) {
            return false;
        }
        
        // Check side effect restriction
        if (hasSideEffect && !allowSideEffectTools) {
            return false;
        }
        
        // Full access allows everything
        if (toolAccessLevel == ToolAccessLevel.FULL) {
            return true;
        }
        
        // TIER_A allows only Tier A tools
        if (toolAccessLevel == ToolAccessLevel.TIER_A) {
            return TIER_A_TOOLS.contains(toolName);
        }
        
        // TIER_A_B allows Tier A and Tier B tools
        if (toolAccessLevel == ToolAccessLevel.TIER_A_B) {
            return TIER_A_TOOLS.contains(toolName) || TIER_B_TOOLS.contains(toolName);
        }
        
        return false;
    }
    
    /**
     * Create a builder for ExecutionPolicy.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for ExecutionPolicy.
     */
    public static class Builder {
        private boolean allowFile = true;
        private boolean allowRag = false;
        private ToolAccessLevel toolAccessLevel = ToolAccessLevel.NONE;
        private int maxToolRounds = 1;
        private boolean allowSideEffectTools = false;
        private String preferredMode = "FAST";
        
        public Builder allowFile(boolean allowFile) {
            this.allowFile = allowFile;
            return this;
        }
        
        public Builder allowRag(boolean allowRag) {
            this.allowRag = allowRag;
            return this;
        }
        
        public Builder toolAccessLevel(ToolAccessLevel toolAccessLevel) {
            this.toolAccessLevel = toolAccessLevel;
            return this;
        }
        
        public Builder maxToolRounds(int maxToolRounds) {
            this.maxToolRounds = maxToolRounds;
            return this;
        }
        
        public Builder allowSideEffectTools(boolean allowSideEffectTools) {
            this.allowSideEffectTools = allowSideEffectTools;
            return this;
        }
        
        public Builder preferredMode(String preferredMode) {
            this.preferredMode = preferredMode;
            return this;
        }
        
        public ExecutionPolicy build() {
            return new ExecutionPolicy(
                allowFile,
                allowRag,
                toolAccessLevel,
                maxToolRounds,
                allowSideEffectTools,
                preferredMode
            );
        }
    }
}
