package com.mrpot.agent.tools.service;

public record ToolContext(
    String traceId,
    String sessionId
) {}
