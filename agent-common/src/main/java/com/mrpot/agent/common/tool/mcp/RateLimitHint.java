package com.mrpot.agent.common.tool.mcp;

public record RateLimitHint(
    Integer maxCallsPerMinute,
    Integer maxCallsPerHour
) {}
