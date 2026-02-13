package com.mrpot.agent.knowledge.controller;

import com.mrpot.agent.common.todo.CreateTodoRequest;
import com.mrpot.agent.common.todo.TodoItem;
import com.mrpot.agent.common.todo.UpdateTodoRequest;
import com.mrpot.agent.knowledge.service.TodoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
@Tag(name = "Todo Management", description = "CRUD APIs for todo items with SSE real-time updates")
public class TodoController {

    private final TodoService todoService;

    @GetMapping
    @Operation(summary = "List all todos", description = "Retrieve all todo items ordered by ID descending")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of todos returned")
    })
    public ResponseEntity<List<TodoItem>> getAllTodos() {
        return ResponseEntity.ok(todoService.getAllTodos());
    }

    @PostMapping
    @Operation(summary = "Create a new todo", description = "Create a new todo item and broadcast via SSE")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Todo created"),
        @ApiResponse(responseCode = "400", description = "Missing title")
    })
    public ResponseEntity<?> createTodo(@RequestBody CreateTodoRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Title is required"));
        }
        TodoItem created = todoService.createTodo(request.title(), request.description());
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a todo", description = "Update todo status/title/description and broadcast via SSE")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Todo updated"),
        @ApiResponse(responseCode = "404", description = "Todo not found")
    })
    public ResponseEntity<?> updateTodo(
            @Parameter(description = "Todo ID", example = "1")
            @PathVariable Long id,
            @RequestBody UpdateTodoRequest request) {
        TodoItem updated = todoService.updateTodo(id, request.title(), request.description(), request.status());
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a todo", description = "Delete a todo item and broadcast via SSE")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Todo deleted"),
        @ApiResponse(responseCode = "404", description = "Todo not found")
    })
    public ResponseEntity<Map<String, Object>> deleteTodo(
            @Parameter(description = "Todo ID", example = "1")
            @PathVariable Long id) {
        boolean deleted = todoService.deleteTodo(id);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
            "deleted", true,
            "id", id,
            "timestamp", Instant.now().toEpochMilli()
        ));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to todo SSE stream",
               description = "Real-time SSE stream of todo changes. Sends current list on connect, then updates on changes.")
    public SseEmitter streamTodos() {
        return todoService.subscribe();
    }
}
