package com.mrpot.agent.common.todo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A single todo item")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TodoItem(
    @Schema(description = "Todo ID", example = "1")
    Long id,

    @Schema(description = "Todo title", example = "Implement SSE integration")
    String title,

    @Schema(description = "Todo description", example = "Add real-time updates via SSE")
    String description,

    @Schema(description = "Todo status", example = "PENDING")
    TodoStatus status,

    @Schema(description = "Creation timestamp (epoch ms)", example = "1707408000000")
    Long createdAt,

    @Schema(description = "Completion timestamp (epoch ms)", example = "1707408060000")
    Long completedAt
) {}
