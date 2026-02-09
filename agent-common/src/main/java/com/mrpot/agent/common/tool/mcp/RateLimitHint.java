package com.mrpot.agent.common.tool.mcp;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Rate limit hints for tool execution")
public record RateLimitHint(
    @Schema(description = "Maximum calls allowed per minute", example = "60")
    Integer maxCallsPerMinute,
    
    @Schema(description = "Maximum calls allowed per hour", example = "1000")
    Integer maxCallsPerHour
) {}
