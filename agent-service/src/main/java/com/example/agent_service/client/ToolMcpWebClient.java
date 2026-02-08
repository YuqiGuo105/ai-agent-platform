package com.example.agent_service.client;

import com.mrpot.agent.common.tool.mcp.CallToolRequest;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ListToolsRequest;
import com.mrpot.agent.common.tool.mcp.ListToolsResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class ToolMcpWebClient implements ToolMcpClient {

  private final WebClient toolsWebClient;

  public ToolMcpWebClient(WebClient toolsWebClient) {
    this.toolsWebClient = toolsWebClient;
  }

  @Override
  public Mono<ListToolsResponse> listTools(ListToolsRequest request) {
    return toolsWebClient.post()
        .uri("/mcp/list_tools")
        .bodyValue(request)
        .retrieve()
        .bodyToMono(ListToolsResponse.class);
  }

  @Override
  public Mono<CallToolResponse> callTool(CallToolRequest request) {
    return toolsWebClient.post()
        .uri("/mcp/call_tool")
        .bodyValue(request)
        .retrieve()
        .bodyToMono(CallToolResponse.class);
  }
}
