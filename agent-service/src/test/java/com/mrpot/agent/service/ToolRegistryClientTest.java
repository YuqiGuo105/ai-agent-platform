package com.mrpot.agent.service;

import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.api.ToolProfile;
import com.mrpot.agent.common.tool.mcp.ListToolsResponse;
import com.mrpot.agent.common.tool.mcp.ToolDefinition;
import com.mrpot.agent.common.util.Json;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolRegistryClientTest {

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
  void ensureFresh_uses_cache_when_ttl_valid() throws Exception {
    ListToolsResponse response = new ListToolsResponse(
        List.of(new ToolDefinition("system.ping", "desc", "1.0", null, null, null, 300L)),
        300L,
        Instant.now()
    );
    String body = Json.MAPPER.writeValueAsString(response);
    server.enqueue(new MockResponse().setBody(body).addHeader("Content-Type", "application/json"));

    WebClient webClient = WebClient.builder()
        .baseUrl(server.url("/").toString())
        .build();
    ToolRegistryClient client = new ToolRegistryClient(webClient);
    ReflectionTestUtils.setField(client, "defaultTtlSeconds", 300L);

    List<ToolDefinition> first = client.ensureFresh(ScopeMode.AUTO, ToolProfile.BASIC, "t1", "s1").block();
    List<ToolDefinition> second = client.ensureFresh(ScopeMode.AUTO, ToolProfile.BASIC, "t1", "s1").block();

    assertEquals(1, first.size());
    assertEquals(1, second.size());
    assertEquals(1, server.getRequestCount());
  }

  public static void main(String[] args) throws Exception {
    ToolRegistryClientTest test = new ToolRegistryClientTest();
    test.setUp();
    try {
      test.ensureFresh_uses_cache_when_ttl_valid();
    } finally {
      test.tearDown();
    }
  }
}
