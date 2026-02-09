package com.mrpot.agent.tools.controller;

import com.mrpot.agent.common.tool.mcp.CallToolRequest;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ListToolsRequest;
import com.mrpot.agent.common.tool.mcp.ListToolsResponse;
import com.mrpot.agent.common.tool.mcp.ToolError;
import com.mrpot.agent.tools.service.ToolContext;
import com.mrpot.agent.tools.service.ToolHandler;
import com.mrpot.agent.tools.service.ToolRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/mcp")
@Tag(name = "MCP Tools", description = "Model Context Protocol - Tool registration and execution")
public class McpToolsController {
  private final ToolRegistry registry;

  public McpToolsController(ToolRegistry registry) {
    this.registry = registry;
  }

  @PostMapping("/list_tools")
  @Operation(
      summary = "List available MCP tools",
      description = "Retrieve all registered MCP tools with their definitions, input/output schemas, and rate limits. " +
                    "Tools are cached based on scope mode and tool profile."
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "List of available tools with metadata",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ListToolsResponse.class),
              examples = @ExampleObject(
                  name = "Tools List",
                  value = "{\"tools\":[{\"name\":\"system.ping\",\"description\":\"Health check\",\"version\":\"1.0.0\"}],\"ttlSeconds\":300}"
              )
          )
      ),
      @ApiResponse(
          responseCode = "400",
          description = "Invalid request parameters"
      )
  })
  public ListToolsResponse listTools(
      @Parameter(
          description = "Request containing scope mode and tool profile for filtering tools",
          required = true,
          schema = @Schema(implementation = ListToolsRequest.class)
      )
      @RequestBody ListToolsRequest request
  ) {
    return new ListToolsResponse(
        registry.listTools(),
        300L,
        Instant.now()
    );
  }

  @PostMapping("/call_tool")
  @Operation(
      summary = "Execute an MCP tool",
      description = "Execute a registered MCP tool by name with provided arguments. " +
                    "Returns execution result or structured error with retry hints."
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "Tool execution result (success or error)",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = CallToolResponse.class),
              examples = {
                  @ExampleObject(
                      name = "Success",
                      value = "{\"ok\":true,\"toolName\":\"system.ping\",\"result\":{\"ok\":true,\"timestamp\":\"2026-02-08T10:00:00Z\"},\"cacheHit\":false,\"ttlHintSeconds\":300}"
                  ),
                  @ExampleObject(
                      name = "Not Found",
                      value = "{\"ok\":false,\"toolName\":\"unknown.tool\",\"error\":{\"code\":\"NOT_FOUND\",\"message\":\"Tool not found\",\"retryable\":false}}"
                  )
              }
          )
      ),
      @ApiResponse(
          responseCode = "400",
          description = "Invalid tool arguments"
      ),
      @ApiResponse(
          responseCode = "408",
          description = "Tool execution timeout"
      )
  })
  public CallToolResponse callTool(
      @Parameter(
          description = "Tool execution request containing tool name, arguments, and context",
          required = true,
          schema = @Schema(implementation = CallToolRequest.class)
      )
      @RequestBody CallToolRequest request
  ) {
    ToolHandler handler = registry.getHandler(request.toolName());

    if (handler == null) {
      return new CallToolResponse(
          false,
          request.toolName(),
          null,
          false,
          null,
          Instant.now(),
          new ToolError(ToolError.NOT_FOUND, "Tool not found: " + request.toolName(), false)
      );
    }

    ToolContext ctx = new ToolContext(request.traceId(), request.sessionId());
    return handler.handle(request.args(), ctx);
  }
}
