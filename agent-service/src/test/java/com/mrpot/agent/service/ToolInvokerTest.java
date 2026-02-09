package com.mrpot.agent.service;

import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.api.ToolProfile;
import com.mrpot.agent.common.tool.mcp.CallToolRequest;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ToolError;
import com.mrpot.agent.common.util.Json;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ToolInvokerTest {

  private MockWebServer server;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void call_returns_timeout_error_on_delay() throws Exception {
    server.enqueue(new MockResponse()
        .setBodyDelay(2, TimeUnit.SECONDS)
        .setBody(Json.MAPPER.writeValueAsString(new CallToolResponse(true, "system.ping", null, false, 1L, Instant.now(), null)))
        .addHeader("Content-Type", "application/json"));

    WebClient webClient = WebClient.builder()
        .baseUrl(server.url("/").toString())
        .build();
    ToolInvoker invoker = new ToolInvoker(webClient);
    ReflectionTestUtils.setField(invoker, "timeoutMs", 100L);

    CallToolRequest request = new CallToolRequest("system.ping", Json.MAPPER.createObjectNode(), ScopeMode.AUTO, ToolProfile.BASIC, "t", "s");
    CallToolResponse response = invoker.call(request).block();

    assertFalse(response.ok());
    assertEquals(ToolError.TIMEOUT, response.error().code());
  }

  @Test
  void call_returns_internal_error_on_500() {
    server.enqueue(new MockResponse().setResponseCode(500));

    WebClient webClient = WebClient.builder()
        .baseUrl(server.url("/").toString())
        .build();
    ToolInvoker invoker = new ToolInvoker(webClient);
    ReflectionTestUtils.setField(invoker, "timeoutMs", 1000L);

    CallToolRequest request = new CallToolRequest("system.ping", Json.MAPPER.createObjectNode(), ScopeMode.AUTO, ToolProfile.BASIC, "t", "s");
    CallToolResponse response = invoker.call(request).block();

    assertFalse(response.ok());
    assertEquals(ToolError.INTERNAL, response.error().code());
  }
}
