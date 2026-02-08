package com.mrpot.agent.common.tool.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RateLimitHint(Integer rps) {}
