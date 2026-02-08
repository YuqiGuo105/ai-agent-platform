package com.mrpot.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrpot.agent.common.api.RagAnswerRequest;
import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.common.tool.mcp.CallToolRequest;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

@Service
public class AnswerStreamOrchestrator {
  private final ToolRegistryClient registryClient;
  private final ToolInvoker toolInvoker;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${mcp.debug.allow-explicit-tool:false}")
  private boolean allowExplicitTool;

  public AnswerStreamOrchestrator(ToolRegistryClient registryClient, ToolInvoker toolInvoker) {
    this.registryClient = registryClient;
    this.toolInvoker = toolInvoker;
  }

  public Flux<SseEnvelope> stream(RagAnswerRequest request, String traceId) {
    AtomicLong seq = new AtomicLong(0);
    return Flux.create(sink -> {
      try {
        sink.next(createEnvelope(StageNames.START, "Starting", null, seq, traceId, request.sessionId()));
        registryClient.ensureFresh(request.scopeMode(), request.toolProfile(), traceId, request.sessionId());
        String debugToolName = extractDebugToolName(request);
        if (debugToolName != null && allowExplicitTool) {
          executeToolCall(debugToolName, request, traceId, seq, sink);
        }
        simulateLlmStream(request, traceId, seq, sink);
        sink.next(createEnvelope(StageNames.ANSWER_FINAL, "Complete", Map.of("answer", "Final answer here"),
            seq, traceId, request.sessionId()));
        sink.complete();
      } catch (Exception e) {
        sink.next(createEnvelope(StageNames.ERROR, "Error: " + e.getMessage(), null, seq, traceId,
            request.sessionId()));
        sink.complete();
      }
    });
  }

  private void executeToolCall(String toolName, RagAnswerRequest request, String traceId, AtomicLong seq,
                               FluxSink<SseEnvelope> sink) {
    sink.next(createEnvelope(StageNames.TOOL_CALL_START, "Calling tool: " + toolName,
        Map.of("toolName", toolName), seq, traceId, request.sessionId()));
    CallToolResponse response = toolInvoker.call(new CallToolRequest(toolName, objectMapper.createObjectNode(),
        request.scopeMode(), request.toolProfile(), traceId, request.sessionId()));
    if (response.ok()) {
      ObjectNode summary = objectMapper.createObjectNode();
      summary.put("toolName", toolName);
      summary.put("success", true);
      summary.set("result", response.result());
      sink.next(createEnvelope(StageNames.TOOL_CALL_RESULT, "Tool call succeeded", summary, seq, traceId,
          request.sessionId()));
    } else {
      sink.next(createEnvelope(StageNames.TOOL_CALL_ERROR,
          "Tool call failed: " + (response.error() == null ? "unknown" : response.error().message()),
          Map.of("toolName", toolName, "error", response.error()), seq, traceId, request.sessionId()));
    }
  }

  private void simulateLlmStream(RagAnswerRequest request, String traceId, AtomicLong seq,
                                 FluxSink<SseEnvelope> sink) {
    String[] chunks = {"Hello ", "this ", "is ", "a ", "test ", "response."};
    for (String chunk : chunks) {
      sink.next(createEnvelope(StageNames.ANSWER_DELTA, chunk, Map.of("delta", chunk), seq, traceId,
          request.sessionId()));
    }
  }

  private String extractDebugToolName(RagAnswerRequest request) {
    if (request.ext() != null && request.ext().containsKey("debugToolName")) {
      return String.valueOf(request.ext().get("debugToolName"));
    }
    return null;
  }

  private SseEnvelope createEnvelope(String stage, String message, Object payload, AtomicLong seq,
                                     String traceId, String sessionId) {
    return new SseEnvelope(stage, message, payload, seq.incrementAndGet(), System.currentTimeMillis(), traceId,
        sessionId);
  }
}
