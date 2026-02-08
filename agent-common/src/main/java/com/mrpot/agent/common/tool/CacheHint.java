package com.mrpot.agent.common.tool;

public record CacheHint(
    Long ttlSeconds,
    Boolean allowCache
) {}
