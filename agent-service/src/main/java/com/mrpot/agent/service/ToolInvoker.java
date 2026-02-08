package com.mrpot.agent.service;

import com.mrpot.agent.common.tool.mcp.CallToolRequest;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ToolError;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class ToolInvoker {
  private final WebClient webClient;

  @Value("${mcp.call.timeout-ms:1200}")
  private long timeoutMs;

  @Value("${mcp.call.max-retries:1}")
  private int maxRetries;

  public ToolInvoker(WebClient mcpWebClient) {
    this.webClient = mcpWebClient;
  }

  public CallToolResponse call(CallToolRequest request) {
    try {
      return webClient.post().uri("/mcp/call_tool").bodyValue(request).retrieve()
          .bodyToMono(CallToolResponse.class)
          .timeout(Duration.ofMillis(timeoutMs))
          .retry(Math.max(0, maxRetries))
          .onErrorResume(e -> {
            ToolError error = e instanceof TimeoutException
                ? new ToolError(ToolError.TIMEOUT, "Tool call timeout", true)
                : new ToolError(ToolError.INTERNAL, e.getMessage(), false);
            return Mono.just(new CallToolResponse(false, request.toolName(), null, false, null,
                Instant.now(), error));
          }).block();
    } catch (Exception e) {
      return new CallToolResponse(false, request.toolName(), null, false, null, Instant.now(),
          new ToolError(ToolError.INTERNAL, "Unexpected error: " + e.getMessage(), false));
    }
  }
}
