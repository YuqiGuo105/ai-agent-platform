package com.mrpot.agent.tools.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ToolDefinition;

public interface ToolHandler {
  String name();

  ToolDefinition definition();

  CallToolResponse handle(JsonNode args, ToolContext ctx);
}
