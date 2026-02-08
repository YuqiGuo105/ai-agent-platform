package com.mrpot.agent.tools.controller;

import com.mrpot.agent.common.tool.mcp.CallToolRequest;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ListToolsRequest;
import com.mrpot.agent.common.tool.mcp.ListToolsResponse;
import com.mrpot.agent.common.tool.mcp.ToolError;
import com.mrpot.agent.tools.service.ToolContext;
import com.mrpot.agent.tools.service.ToolHandler;
import com.mrpot.agent.tools.service.ToolRegistry;
import java.time.Instant;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mcp")
public class McpToolsController {
  private final ToolRegistry registry;

  public McpToolsController(ToolRegistry registry) {
    this.registry = registry;
  }

  @PostMapping("/list_tools")
  public ListToolsResponse listTools(@RequestBody ListToolsRequest request) {
    return new ListToolsResponse(registry.listTools(), 300L, Instant.now());
  }

  @PostMapping("/call_tool")
  public CallToolResponse callTool(@RequestBody CallToolRequest request) {
    ToolHandler handler = registry.getHandler(request.toolName());
    if (handler == null) {
      return new CallToolResponse(false, request.toolName(), null, false, null, Instant.now(),
          new ToolError(ToolError.NOT_FOUND, "Tool not found: " + request.toolName(), false));
    }
    ToolContext ctx = new ToolContext(request.traceId(), request.sessionId());
    return handler.handle(request.args(), ctx);
  }
}
