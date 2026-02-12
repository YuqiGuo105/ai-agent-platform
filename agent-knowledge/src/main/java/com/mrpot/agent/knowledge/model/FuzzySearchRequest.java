package com.mrpot.agent.knowledge.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Fuzzy search request for KB documents")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FuzzySearchRequest(

    @Schema(description = "Search keyword for fuzzy matching", example = "machine learning")
    String keyword,

    @Schema(description = "Optional: filter by document type", example = "FAQ")
    String docType,

    @Schema(description = "Page number (0-indexed)", example = "0", defaultValue = "0")
    Integer page,

    @Schema(description = "Page size", example = "20", defaultValue = "20")
    Integer size
) {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    public int resolvePage() {
        return (page == null || page < 0) ? DEFAULT_PAGE : page;
    }

    public int resolveSize() {
        return (size == null || size <= 0) ? DEFAULT_SIZE : Math.min(size, 100);
    }
}
