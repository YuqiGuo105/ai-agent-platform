package com.example.agent_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mcp")
public record McpProperties(
    String toolsBaseUrl,
    long registryDefaultTtlSeconds,
    long callTimeoutMs,
    int callMaxRetries,
    boolean debugAllowExplicitTool
) {
  public McpProperties {
    toolsBaseUrl = (toolsBaseUrl == null || toolsBaseUrl.isBlank()) ? "http://localhost:8082" : toolsBaseUrl;
    registryDefaultTtlSeconds = registryDefaultTtlSeconds <= 0 ? 300 : registryDefaultTtlSeconds;
    callTimeoutMs = callTimeoutMs <= 0 ? 1200 : callTimeoutMs;
    callMaxRetries = Math.max(0, callMaxRetries);
  }
}
