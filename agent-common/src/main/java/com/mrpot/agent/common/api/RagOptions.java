package com.mrpot.agent.common.api;

public record RagOptions(
    Integer topK,
    Double minScore,
    Boolean emitUiBlocks,
    Boolean enableRag,
    Boolean enableTools
) {}
