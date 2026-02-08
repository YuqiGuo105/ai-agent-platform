package com.example.agent_service.repository;

import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.api.ToolProfile;
import com.mrpot.agent.common.tool.mcp.ToolDefinition;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class ToolRegistryRepository {

  private final Map<RegistryKey, CachedTools> cache = new ConcurrentHashMap<>();

  public Optional<CachedTools> getFresh(ScopeMode scopeMode, ToolProfile toolProfile, Instant now) {
    CachedTools cached = cache.get(new RegistryKey(scopeMode, toolProfile));
    if (cached == null || now.isAfter(cached.expireAt())) {
      return Optional.empty();
    }
    return Optional.of(cached);
  }

  public void put(ScopeMode scopeMode, ToolProfile toolProfile, List<ToolDefinition> tools, Instant expireAt) {
    cache.put(new RegistryKey(scopeMode, toolProfile), new CachedTools(tools, expireAt));
  }

  public record RegistryKey(ScopeMode scopeMode, ToolProfile toolProfile) {}

  public record CachedTools(List<ToolDefinition> tools, Instant expireAt) {}
}
