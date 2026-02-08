package com.example.agent_service.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrpot.agent.common.api.RagAnswerRequest;
import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.api.ToolProfile;
import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.common.tool.mcp.CallToolRequest;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class AnswerStreamService {

  private final ToolRegistryService toolRegistryService;
  private final ToolInvokerService toolInvokerService;

  public AnswerStreamService(ToolRegistryService toolRegistryService, ToolInvokerService toolInvokerService) {
    this.toolRegistryService = toolRegistryService;
    this.toolInvokerService = toolInvokerService;
  }

  public Flux<SseEnvelope> stream(RagAnswerRequest request) {
    String traceId = UUID.randomUUID().toString();
    String sessionId = request.resolveSession().id();
    ScopeMode scopeMode = request.resolveScopeMode();
    ToolProfile toolProfile = request.toolProfile() == null ? ToolProfile.BASIC : request.toolProfile();
    AtomicLong seq = new AtomicLong(0L);

    SseEnvelope start = envelope(StageNames.START, "Start", null, seq, traceId, sessionId);

    Flux<SseEnvelope> toolEvents = toolRegistryService.ensureFresh(scopeMode, toolProfile, traceId, sessionId)
        .then(resolveToolCall(request, scopeMode, toolProfile, traceId, sessionId)
            .flatMapMany(call -> {
              if (call == null) {
                return Flux.empty();
              }
              String callId = "c1";
              SseEnvelope startEvt = envelope(StageNames.TOOL_CALL_START, "Calling tool", Map.of(
                  "callId", callId,
                  "toolName", call.toolName(),
                  "argsSummary", call.args().toString()
              ), seq, traceId, sessionId);

              return toolInvokerService.call(call)
                  .map(resp -> toToolResultEnvelope(resp, callId, seq, traceId, sessionId))
                  .flux()
                  .startWith(startEvt);
            }));

    Flux<SseEnvelope> answerDeltas = Flux.fromIterable(mockAnswerChunks(request.question()))
        .map(chunk -> envelope(StageNames.ANSWER_DELTA, "chunk", chunk, seq, traceId, sessionId));

    SseEnvelope finalEvt = envelope(StageNames.ANSWER_FINAL, "done", "", seq, traceId, sessionId);

    return Flux.concat(
        Flux.just(start),
        toolEvents,
        answerDeltas,
        Flux.just(finalEvt)
    );
  }

  private Mono<CallToolRequest> resolveToolCall(
      RagAnswerRequest request,
      ScopeMode scopeMode,
      ToolProfile toolProfile,
      String traceId,
      String sessionId
  ) {
    if (request.question() == null) {
      return Mono.empty();
    }
    String question = request.question().trim().toLowerCase(Locale.ROOT);
    String toolName;
    ObjectNode args = JsonNodeFactory.instance.objectNode();
    if (question.startsWith("/time")) {
      toolName = "system.time";
      args.put("tz", "UTC");
    } else if (question.startsWith("/ping")) {
      toolName = "system.ping";
    } else {
      return Mono.empty();
    }

    return Mono.just(new CallToolRequest(toolName, args, scopeMode, toolProfile, traceId, sessionId));
  }

  private SseEnvelope toToolResultEnvelope(
      CallToolResponse response,
      String callId,
      AtomicLong seq,
      String traceId,
      String sessionId
  ) {
    if (response.ok()) {
      return envelope(StageNames.TOOL_CALL_RESULT, "Tool ok", Map.of(
          "callId", callId,
          "toolName", response.toolName(),
          "summary", response.result() == null ? "ok" : response.result().toString()
      ), seq, traceId, sessionId);
    }

    String code = response.error() == null ? "UNKNOWN" : response.error().code();
    return envelope(StageNames.TOOL_CALL_ERROR, "Tool failed", Map.of(
        "callId", callId,
        "toolName", response.toolName(),
        "errorCode", code
    ), seq, traceId, sessionId);
  }

  private SseEnvelope envelope(String stage, String message, Object payload, AtomicLong seq, String traceId, String sessionId) {
    return new SseEnvelope(stage, message, payload, seq.incrementAndGet(), Instant.now().toEpochMilli(), traceId, sessionId);
  }

  private List<String> mockAnswerChunks(String question) {
    String safeQuestion = question == null ? "" : question;
    List<String> chunks = new ArrayList<>();
    chunks.add("I received your question: ");
    chunks.add(safeQuestion);
    chunks.add(". This is Sprint-1 fallback answer.");
    return chunks;
  }
}
