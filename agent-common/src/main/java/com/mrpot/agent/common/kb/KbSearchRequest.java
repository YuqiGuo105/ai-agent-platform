package com.mrpot.agent.common.kb;

import java.util.Map;

public record KbSearchRequest(
    String query,
    Integer topK,
    Double minScore,
    Map<String, Object> filters
) {
    private static final int DEFAULT_TOP_K = 5;
    private static final double DEFAULT_MIN_SCORE = 0.2;

    public KbSearchRequest {
        topK = (topK == null || topK <= 0) ? DEFAULT_TOP_K : topK;
        minScore = (minScore == null || minScore < 0) ? DEFAULT_MIN_SCORE : minScore;
    }
}
