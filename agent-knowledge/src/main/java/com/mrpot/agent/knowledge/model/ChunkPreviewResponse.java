package com.mrpot.agent.knowledge.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "Response containing chunk previews and metadata")
public record ChunkPreviewResponse(
        @Schema(description = "Detected document type") String docType,
        @Schema(description = "Chunks ready for review") List<ChunkPreview> chunks,
        @Schema(description = "Optional metadata echoed back") Map<String, Object> metadata
) {}
