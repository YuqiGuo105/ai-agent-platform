package com.mrpot.agent.common.todo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request body for creating a new todo")
public record CreateTodoRequest(
    @Schema(description = "Todo title", example = "Implement SSE integration", requiredMode = Schema.RequiredMode.REQUIRED)
    String title,

    @Schema(description = "Todo description", example = "Add real-time updates via SSE")
    String description
) {}
