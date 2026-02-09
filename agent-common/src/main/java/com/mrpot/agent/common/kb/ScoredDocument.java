package com.mrpot.agent.common.kb;

/**
 * A wrapper that combines a KbDocument with its similarity score.
 * Used for vector search results where documents are ranked by relevance.
 */
public record ScoredDocument(
    KbDocument document,
    double score
) {}
