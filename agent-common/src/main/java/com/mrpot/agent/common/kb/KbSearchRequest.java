package com.mrpot.agent.common.kb;

import java.util.Map;

public record KbSearchRequest(
    String query,
    Integer topK,
    Double minScore,
    Map<String, Object> filters
) {}
