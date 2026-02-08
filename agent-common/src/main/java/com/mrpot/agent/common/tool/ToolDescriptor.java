package com.mrpot.agent.common.tool;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolDescriptor(
    String name,
    String description,
    String version,
    ToolSchema inputSchema,
    ToolSchema outputSchema,
    String permission,
    CacheHint cacheHint
) {}
