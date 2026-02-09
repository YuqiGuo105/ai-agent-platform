package com.mrpot.agent.tools.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.api.ToolProfile;
import com.mrpot.agent.common.tool.mcp.CallToolRequest;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ListToolsRequest;
import com.mrpot.agent.common.tool.mcp.ListToolsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class McpToolsControllerTest {

  @Autowired
  private WebTestClient webTestClient;

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void list_tools_returns_registered_tools() {
    ListToolsRequest request = new ListToolsRequest(ScopeMode.AUTO, ToolProfile.BASIC, "t1", "s1");

    ListToolsResponse response = webTestClient.post()
        .uri("/mcp/list_tools")
        .bodyValue(request)
        .exchange()
        .expectStatus().isOk()
        .expectBody(ListToolsResponse.class)
        .returnResult()
        .getResponseBody();

    Set<String> names = response.tools().stream().map(t -> t.name()).collect(java.util.stream.Collectors.toSet());
    assertTrue(names.contains("system.ping"));
    assertTrue(names.contains("system.time"));
  }

  @Test
  void call_tool_returns_ping_result() {
    CallToolRequest request = new CallToolRequest(
        "system.ping",
        mapper.createObjectNode(),
        ScopeMode.AUTO,
        ToolProfile.BASIC,
        "t1",
        "s1"
    );

    CallToolResponse response = webTestClient.post()
        .uri("/mcp/call_tool")
        .bodyValue(request)
        .exchange()
        .expectStatus().isOk()
        .expectBody(CallToolResponse.class)
        .returnResult()
        .getResponseBody();

    assertTrue(response.ok());
  }
}
