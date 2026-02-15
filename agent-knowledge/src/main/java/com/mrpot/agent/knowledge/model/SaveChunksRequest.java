package com.mrpot.agent.knowledge.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "Request to persist reviewed chunks into KB")
public record SaveChunksRequest(
        @Schema(description = "Document type to store", example = "pdf") String docType,
        @Schema(description = "Metadata applied to all chunks") Map<String, Object> metadata,
        @Schema(description = "Chunks to save") List<Chunk> chunks
) {
    @Schema(description = "Chunk payload for saving")
    public record Chunk(
            @Schema(description = "Chunk index (0-based)") int index,
            @Schema(description = "Chunk content") String content,
            @Schema(description = "Embedding vector") float[] embedding,
            @Schema(description = "Optional per-chunk metadata") Map<String, Object> metadata
    ) {}
}
