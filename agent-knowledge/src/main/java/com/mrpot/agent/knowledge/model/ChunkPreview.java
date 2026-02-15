package com.mrpot.agent.knowledge.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Preview of a chunk with embedding")
public record ChunkPreview(
        @Schema(description = "Chunk index (0-based)") int index,
        @Schema(description = "Chunk text content") String content,
        @Schema(description = "Character length of the chunk") int charCount,
        @Schema(description = "Embedding vector for this chunk") float[] embedding
) {}
