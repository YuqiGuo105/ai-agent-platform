package com.mrpot.agent.service;

import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.api.ToolProfile;
import com.mrpot.agent.common.tool.mcp.ListToolsRequest;
import com.mrpot.agent.common.tool.mcp.ListToolsResponse;
import com.mrpot.agent.common.tool.mcp.ToolDefinition;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class ToolRegistryClient {
  private final WebClient webClient;
  private final Map<RegistryKey, CachedTools> cache = new ConcurrentHashMap<>();

  @Value("${mcp.registry.default-ttl-seconds:300}")
  private long defaultTtlSeconds;

  public ToolRegistryClient(WebClient mcpWebClient) {
    this.webClient = mcpWebClient;
  }

  public List<ToolDefinition> ensureFresh(ScopeMode scopeMode, ToolProfile toolProfile, String traceId,
                                          String sessionId) {
    RegistryKey key = new RegistryKey(scopeMode, toolProfile);
    CachedTools cached = cache.get(key);
    if (cached != null && cached.expireAt().isAfter(Instant.now())) {
      return cached.tools();
    }
    try {
      ListToolsResponse response = webClient.post().uri("/mcp/list_tools")
          .bodyValue(new ListToolsRequest(scopeMode, toolProfile, traceId, sessionId)).retrieve()
          .bodyToMono(ListToolsResponse.class).block();
      if (response != null) {
        long ttl = response.ttlSeconds() != null ? response.ttlSeconds() : defaultTtlSeconds;
        CachedTools newCache = new CachedTools(response.tools(), Instant.now().plusSeconds(ttl));
        cache.put(key, newCache);
        return response.tools();
      }
    } catch (Exception e) {
      if (cached != null) {
        return cached.tools();
      }
      throw new RuntimeException("Failed to fetch tools and no cache available", e);
    }
    return List.of();
  }

  private record RegistryKey(ScopeMode scopeMode, ToolProfile toolProfile) {}

  private record CachedTools(List<ToolDefinition> tools, Instant expireAt) {}
}
