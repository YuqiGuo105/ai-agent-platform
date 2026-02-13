package com.mrpot.agent.common.todo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request body for updating a todo")
public record UpdateTodoRequest(
    @Schema(description = "New title (optional)")
    String title,

    @Schema(description = "New description (optional)")
    String description,

    @Schema(description = "New status", example = "COMPLETED", requiredMode = Schema.RequiredMode.REQUIRED)
    TodoStatus status
) {}
