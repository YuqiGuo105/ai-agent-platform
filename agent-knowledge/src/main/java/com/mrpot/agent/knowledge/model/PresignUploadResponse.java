package com.mrpot.agent.knowledge.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response containing presigned upload URL and resulting file URL")
public record PresignUploadResponse(
        @Schema(description = "HTTP method to use", example = "PUT") String method,
        @Schema(description = "Presigned URL for direct upload") String uploadUrl,
        @Schema(description = "Resulting file URL accessible by backend") String fileUrl,
        @Schema(description = "Object key inside bucket") String objectKey,
        @Schema(description = "Target bucket") String bucket
) {}
