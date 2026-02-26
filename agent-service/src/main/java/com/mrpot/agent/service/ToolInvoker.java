package com.mrpot.agent.service;

import com.mrpot.agent.common.telemetry.RunContext;
import com.mrpot.agent.common.tool.mcp.CallToolRequest;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ToolError;
import com.mrpot.agent.service.telemetry.ToolCallTelemetryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Service
public class ToolInvoker {
  private final WebClient webClient;
  private final ToolCallTelemetryWrapper telemetryWrapper;

  @Value("${mcp.call.timeout-ms:1200}")
  private long timeoutMs;

  @Value("${mcp.call.max-retries:1}")
  private int maxRetries;

  @Autowired
  public ToolInvoker(WebClient mcpWebClient, ToolCallTelemetryWrapper telemetryWrapper) {
    this.webClient = mcpWebClient;
    this.telemetryWrapper = telemetryWrapper;
  }

  /**
   * Execute a tool call with telemetry.
   *
   * @param request the tool call request
   * @return the tool call response
   */
  public Mono<CallToolResponse> call(CallToolRequest request) {
    return call(request, (String) null);
  }

  /**
   * Execute a tool call with telemetry and run context.
   *
   * @param request the tool call request
   * @param runId the run ID for tracing (optional, but telemetry is always emitted)
   * @return the tool call response
   */
  public Mono<CallToolResponse> call(CallToolRequest request, String runId) {
    // Always wrap with telemetry - runId may be null but events are still valuable
    return telemetryWrapper.wrapCall(request, runId, this::doCall);
  }

  /**
   * Execute a tool call with full run context.
   *
   * @param request the tool call request
   * @param runContext the run context for tracing (optional, but telemetry is always emitted)
   * @return the tool call response
   */
  public Mono<CallToolResponse> call(CallToolRequest request, RunContext runContext) {
    // Always wrap with telemetry - runContext may be null but events are still valuable
    return telemetryWrapper.wrapCall(request, runContext, this::doCall);
  }

  /**
   * Internal method that performs the actual tool call.
   */
  private Mono<CallToolResponse> doCall(CallToolRequest request) {
    return webClient.post()
        .uri("/mcp/call_tool")
        .bodyValue(request)
        .retrieve()
        .bodyToMono(CallToolResponse.class)
        .timeout(Duration.ofMillis(timeoutMs))
        .onErrorResume(e -> {
          ToolError error;
          if (e instanceof java.util.concurrent.TimeoutException) {
            error = new ToolError(ToolError.TIMEOUT, "Tool call timeout", true);
          } else {
            error = new ToolError(ToolError.INTERNAL, e.getMessage(), false);
          }
          return Mono.just(new CallToolResponse(
              false,
              request.toolName(),
              null,
              false,
              null,
              Instant.now(),
              error
          ));
        })
        .onErrorResume(e -> Mono.just(new CallToolResponse(
            false,
            request.toolName(),
            null,
            false,
            null,
            Instant.now(),
            new ToolError(ToolError.INTERNAL, "Unexpected error: " + e.getMessage(), false)
        )));
  }
}
