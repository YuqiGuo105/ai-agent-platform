package com.example.agent_service.service;

import com.example.agent_service.client.ToolMcpClient;
import com.example.agent_service.config.McpProperties;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.api.ToolProfile;
import com.mrpot.agent.common.tool.mcp.CallToolRequest;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

class ToolInvokerServiceTest {

  @Test
  void timeout_returns_error_response() {
    ToolMcpClient client = new ToolMcpClient() {
      @Override
      public Mono<com.mrpot.agent.common.tool.mcp.ListToolsResponse> listTools(
          com.mrpot.agent.common.tool.mcp.ListToolsRequest request
      ) {
        return Mono.empty();
      }

      @Override
      public Mono<com.mrpot.agent.common.tool.mcp.CallToolResponse> callTool(
          CallToolRequest request
      ) {
        return Mono.delay(Duration.ofMillis(50)).then(Mono.never());
      }
    };

    ToolInvokerService service = new ToolInvokerService(client, new McpProperties("http://localhost", 300, 10, 0, true));
    CallToolRequest req = new CallToolRequest(
        "system.time",
        JsonNodeFactory.instance.objectNode(),
        ScopeMode.GENERAL,
        ToolProfile.BASIC,
        "t",
        "s"
    );

    StepVerifier.create(service.call(req))
        .assertNext(resp -> {
          org.junit.jupiter.api.Assertions.assertFalse(resp.ok());
          org.junit.jupiter.api.Assertions.assertEquals("TIMEOUT", resp.error().code());
        })
        .verifyComplete();
  }
}
