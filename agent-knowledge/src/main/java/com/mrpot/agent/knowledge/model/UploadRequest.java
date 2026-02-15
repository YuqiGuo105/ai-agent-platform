package com.mrpot.agent.knowledge.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "Request for uploading a document via S3 URL")
public record UploadRequest(
        @Schema(description = "S3 file URL", required = true) String fileUrl,
        @Schema(description = "Optional metadata") Map<String, Object> metadata
) {}
