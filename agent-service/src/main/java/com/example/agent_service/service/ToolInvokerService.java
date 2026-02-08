package com.example.agent_service.service;

import com.example.agent_service.client.ToolMcpClient;
import com.example.agent_service.config.McpProperties;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.mrpot.agent.common.tool.mcp.CallToolRequest;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ToolError;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Service
public class ToolInvokerService {

  private static final Set<String> RETRYABLE_TOOLS = Set.of("system.ping", "system.time");

  private final ToolMcpClient toolMcpClient;
  private final McpProperties properties;

  public ToolInvokerService(ToolMcpClient toolMcpClient, McpProperties properties) {
    this.toolMcpClient = toolMcpClient;
    this.properties = properties;
  }

  public Mono<CallToolResponse> call(CallToolRequest request) {
    Mono<CallToolResponse> remote = toolMcpClient.callTool(request)
        .timeout(Duration.ofMillis(properties.callTimeoutMs()));

    if (RETRYABLE_TOOLS.contains(request.toolName()) && properties.callMaxRetries() > 0) {
      remote = remote.retryWhen(Retry.max(properties.callMaxRetries()));
    }

    return remote.onErrorResume(ex -> Mono.just(new CallToolResponse(
        false,
        request.toolName(),
        JsonNodeFactory.instance.objectNode(),
        false,
        0L,
        Instant.now(),
        new ToolError("TIMEOUT", "tool call timeout", true)
    )));
  }
}
