package com.mrpot.agent.knowledge.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "Request for direct text upload")
public record TextUploadRequest(
        @Schema(description = "Plain text content", required = true) String text,
        @Schema(description = "Optional metadata") Map<String, Object> metadata
) {}
