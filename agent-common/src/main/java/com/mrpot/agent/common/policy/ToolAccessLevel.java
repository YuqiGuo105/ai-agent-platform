package com.mrpot.agent.common.policy;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Tool access level for execution policies.
 * Defines what categories of tools a user can access.
 */
@Schema(description = "Tool access level for execution policies")
public enum ToolAccessLevel {
    
    @Schema(description = "No tool access allowed")
    NONE,
    
    @Schema(description = "Tier A only: system.ping, system.time")
    TIER_A,
    
    @Schema(description = "Tier A + B: read-only tools")
    TIER_A_B,
    
    @Schema(description = "Full tool access")
    FULL
}
