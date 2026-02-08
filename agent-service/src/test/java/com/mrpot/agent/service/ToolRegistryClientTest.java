package com.mrpot.agent.service;

import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.api.ToolProfile;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolRegistryClientTest {
  MockWebServer server;
  ToolRegistryClient client;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    client = new ToolRegistryClient(WebClient.builder().baseUrl(server.url("/").toString()).build());
    ReflectionTestUtils.setField(client, "defaultTtlSeconds", 300L);
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void ensureFresh_uses_cache_before_ttl() {
    server.enqueue(new MockResponse().setBody("{\"tools\":[{\"name\":\"system.ping\",\"description\":\"d\",\"version\":\"1\"}],\"ttlSeconds\":300,\"sourceTs\":\"2024-01-01T00:00:00Z\"}")
        .addHeader("Content-Type", "application/json"));

    var first = client.ensureFresh(ScopeMode.AUTO, ToolProfile.DEFAULT, "t", "s");
    var second = client.ensureFresh(ScopeMode.AUTO, ToolProfile.DEFAULT, "t", "s");

    assertEquals(1, first.size());
    assertEquals(1, second.size());
    assertEquals(1, server.getRequestCount());
  }
}
