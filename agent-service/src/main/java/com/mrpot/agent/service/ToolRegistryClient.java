package com.mrpot.agent.service;

import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.api.ToolProfile;
import com.mrpot.agent.common.tool.mcp.ListToolsRequest;
import com.mrpot.agent.common.tool.mcp.ListToolsResponse;
import com.mrpot.agent.common.tool.mcp.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ToolRegistryClient {
  private static final Logger log = LoggerFactory.getLogger(ToolRegistryClient.class);
  
  private final WebClient webClient;
  private final Map<RegistryKey, CachedTools> cache = new ConcurrentHashMap<>();

  @Value("${mcp.registry.default-ttl-seconds:300}")
  private long defaultTtlSeconds;

  public ToolRegistryClient(WebClient mcpWebClient) {
    this.webClient = mcpWebClient;
  }

  public Mono<List<ToolDefinition>> ensureFresh(
      ScopeMode scopeMode,
      ToolProfile toolProfile,
      String traceId,
      String sessionId
  ) {
    RegistryKey key = new RegistryKey(scopeMode, toolProfile);
    CachedTools cached = cache.get(key);

    if (cached != null && cached.expireAt.isAfter(Instant.now())) {
      return Mono.just(cached.tools);
    }

    ListToolsRequest request = new ListToolsRequest(scopeMode, toolProfile, traceId, sessionId);
    return webClient.post()
        .uri("/mcp/list_tools")
        .bodyValue(request)
        .retrieve()
        .bodyToMono(ListToolsResponse.class)
        .map(response -> {
          if (response == null || response.tools() == null) {
            return List.<ToolDefinition>of();
          }
          long ttl = response.ttlSeconds() != null ? response.ttlSeconds() : defaultTtlSeconds;
          CachedTools newCache = new CachedTools(
              response.tools(),
              Instant.now().plusSeconds(ttl)
          );
          cache.put(key, newCache);
          return response.tools();
        })
        .onErrorResume(e -> {
          if (cached != null) {
            log.warn("Failed to fetch tools from MCP service, using stale cache: {}", e.getMessage());
            return Mono.just(cached.tools);
          }
          log.warn("Failed to fetch tools from MCP service and no cache available. Continuing without tools: {}", e.getMessage());
          return Mono.just(List.of());
        });
  }

  private record RegistryKey(ScopeMode scopeMode, ToolProfile toolProfile) {
  }

  private record CachedTools(List<ToolDefinition> tools, Instant expireAt) {
  }
}
