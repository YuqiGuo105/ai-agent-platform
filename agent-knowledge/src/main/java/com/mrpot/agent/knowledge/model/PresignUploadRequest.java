package com.mrpot.agent.knowledge.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to generate a presigned S3 upload URL")
public record PresignUploadRequest(
        @Schema(description = "Original filename", required = true) String filename,
        @Schema(description = "MIME content type", example = "application/pdf") String contentType
) {}
