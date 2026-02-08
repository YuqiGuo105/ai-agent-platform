package com.mrpot.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.api.ToolProfile;
import com.mrpot.agent.common.tool.mcp.CallToolRequest;
import com.mrpot.agent.common.tool.mcp.ToolError;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ToolInvokerTest {
  MockWebServer server;
  ToolInvoker invoker;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    invoker = new ToolInvoker(WebClient.builder().baseUrl(server.url("/").toString()).build());
    ReflectionTestUtils.setField(invoker, "timeoutMs", 100L);
    ReflectionTestUtils.setField(invoker, "maxRetries", 0);
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void call_timeout_mapped_to_tool_error() {
    server.enqueue(new MockResponse().setBodyDelay(500, java.util.concurrent.TimeUnit.MILLISECONDS)
        .setBody("{}").addHeader("Content-Type", "application/json"));
    var req = new CallToolRequest("system.ping", new ObjectMapper().createObjectNode(), ScopeMode.AUTO,
        ToolProfile.DEFAULT, "t", "s");
    var response = invoker.call(req);
    assertFalse(response.ok());
    assertEquals(ToolError.TIMEOUT, response.error().code());
  }

  @Test
  void call_server_error_mapped_to_internal() {
    server.enqueue(new MockResponse().setResponseCode(500));
    var req = new CallToolRequest("system.ping", new ObjectMapper().createObjectNode(), ScopeMode.AUTO,
        ToolProfile.DEFAULT, "t", "s");
    var response = invoker.call(req);
    assertFalse(response.ok());
    assertEquals(ToolError.INTERNAL, response.error().code());
  }
}
