package com.example.agent_service.client;

import com.mrpot.agent.common.tool.mcp.CallToolRequest;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ListToolsRequest;
import com.mrpot.agent.common.tool.mcp.ListToolsResponse;
import reactor.core.publisher.Mono;

public interface ToolMcpClient {
  Mono<ListToolsResponse> listTools(ListToolsRequest request);

  Mono<CallToolResponse> callTool(CallToolRequest request);
}
