package com.mrpot.agent.knowledge.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Generic paginated response")
public record PagedResponse<T>(
        @Schema(description = "Current page content")
        List<T> content,
        @Schema(description = "Current page number (0-indexed)")
        int page,
        @Schema(description = "Requested page size")
        int size,
        @Schema(description = "Total number of elements")
        long totalElements,
        @Schema(description = "Total number of pages")
        int totalPages
) {
}
