package com.example.agent_service.service;

import com.example.agent_service.client.ToolMcpClient;
import com.example.agent_service.config.McpProperties;
import com.example.agent_service.repository.ToolRegistryRepository;
import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.api.ToolProfile;
import com.mrpot.agent.common.tool.mcp.ListToolsRequest;
import com.mrpot.agent.common.tool.mcp.ToolDefinition;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ToolRegistryService {

  private final ToolMcpClient toolMcpClient;
  private final ToolRegistryRepository repository;
  private final McpProperties properties;

  public ToolRegistryService(ToolMcpClient toolMcpClient, ToolRegistryRepository repository, McpProperties properties) {
    this.toolMcpClient = toolMcpClient;
    this.repository = repository;
    this.properties = properties;
  }

  public Mono<List<ToolDefinition>> ensureFresh(
      ScopeMode scopeMode,
      ToolProfile toolProfile,
      String traceId,
      String sessionId
  ) {
    Instant now = Instant.now();
    return repository.getFresh(scopeMode, toolProfile, now)
        .map(cached -> Mono.just(cached.tools()))
        .orElseGet(() -> refresh(scopeMode, toolProfile, traceId, sessionId));
  }

  private Mono<List<ToolDefinition>> refresh(ScopeMode scopeMode, ToolProfile toolProfile, String traceId, String sessionId) {
    var request = new ListToolsRequest(scopeMode, toolProfile, traceId, sessionId);
    return toolMcpClient.listTools(request)
        .map(resp -> {
          long ttl = resp.ttlSeconds() == null || resp.ttlSeconds() <= 0
              ? properties.registryDefaultTtlSeconds()
              : resp.ttlSeconds();
          repository.put(scopeMode, toolProfile, resp.tools(), Instant.now().plusSeconds(ttl));
          return resp.tools();
        })
        .onErrorResume(ex -> Mono.just(List.of()));
  }
}
