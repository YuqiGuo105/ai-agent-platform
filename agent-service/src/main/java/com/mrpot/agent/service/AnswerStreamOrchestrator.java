package com.mrpot.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrpot.agent.common.api.RagAnswerRequest;
import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.common.telemetry.RunLogEnvelope;
import com.mrpot.agent.common.tool.mcp.CallToolRequest;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.service.telemetry.RunLogPublisher;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class AnswerStreamOrchestrator {
  private final ToolRegistryClient registryClient;
  private final ToolInvoker toolInvoker;
  private final RagAnswerService ragAnswerService;
  private final RunLogPublisher publisher;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${mcp.debug.allow-explicit-tool:false}")
  private boolean allowExplicitTool;

  public AnswerStreamOrchestrator(
      ToolRegistryClient registryClient,
      ToolInvoker toolInvoker,
      RagAnswerService ragAnswerService,
      RunLogPublisher publisher
  ) {
    this.registryClient = registryClient;
    this.toolInvoker = toolInvoker;
    this.ragAnswerService = ragAnswerService;
    this.publisher = publisher;
  }

  public Flux<SseEnvelope> stream(RagAnswerRequest request, String traceId) {
    AtomicLong seq = new AtomicLong(0);
    String runId = UUID.randomUUID().toString();
    long t0 = System.currentTimeMillis();

    // Publish run.start telemetry event
    publisher.publish(new RunLogEnvelope(
        "1", "run.start", runId, traceId, request.sessionId(), "userId_placeholder",
        "GENERAL", "deepseek", Instant.now(),
        Map.of("question", truncate(request.question(), 3800))
    ));

    Flux<SseEnvelope> start = Flux.just(createEnvelope(
        StageNames.START,
        "Starting",
        null,
        seq,
        traceId,
        request.sessionId()
    ));

    Mono<Void> ensureFresh = registryClient.ensureFresh(
        request.scopeMode(),
        request.toolProfile(),
        traceId,
        request.sessionId()
    ).then();

    // File extraction flow
    List<String> fileUrls = request.resolveFileUrls(3);
    Flux<SseEnvelope> fileExtractFlux = ragAnswerService.generateFileExtractionEvents(
        fileUrls,
        traceId,
        request.sessionId(),
        seq
    );

    Flux<SseEnvelope> toolCallFlux = Mono.fromSupplier(() -> extractDebugToolName(request))
        .filter(name -> name != null && allowExplicitTool)
        .flatMapMany(name -> executeToolCall(name, request, traceId, seq));

    Flux<SseEnvelope> answerFlux = simulateLlmStream(request, traceId, seq);

    Flux<SseEnvelope> finalFlux = Flux.defer(() -> {
      long totalMs = System.currentTimeMillis() - t0;
      // Publish run.final telemetry event
      publisher.publish(new RunLogEnvelope(
          "1", "run.final", runId, traceId, request.sessionId(), "userId_placeholder",
          "GENERAL", "deepseek", Instant.now(),
          Map.of(
              "answerFinal", truncate("Final answer here", 11000),
              "totalLatencyMs", totalMs
          )
      ));
      return Flux.just(createEnvelope(
          StageNames.ANSWER_FINAL,
          "Complete",
          Map.of("answer", "Final answer here"),
          seq,
          traceId,
          request.sessionId()
      ));
    });

    return start
        .concatWith(ensureFresh.thenMany(fileExtractFlux))
        .concatWith(toolCallFlux)
        .concatWith(answerFlux)
        .concatWith(finalFlux)
        .onErrorResume(e -> {
          // Publish run.failed telemetry event
          publisher.publish(new RunLogEnvelope(
              "1", "run.failed", runId, traceId, request.sessionId(), "userId_placeholder",
              "GENERAL", "deepseek", Instant.now(),
              Map.of("errorCode", e.getClass().getSimpleName())
          ));
          return Flux.just(createEnvelope(
              StageNames.ERROR,
              "Error: " + e.getMessage(),
              null,
              seq,
              traceId,
              request.sessionId()
          ));
        });
  }

  private Flux<SseEnvelope> executeToolCall(
      String toolName,
      RagAnswerRequest request,
      String traceId,
      AtomicLong seq
  ) {
    Flux<SseEnvelope> start = Flux.just(createEnvelope(
        StageNames.TOOL_CALL_START,
        "Calling tool: " + toolName,
        Map.of("toolName", toolName),
        seq,
        traceId,
        request.sessionId()
    ));

    CallToolRequest callRequest = new CallToolRequest(
        toolName,
        objectMapper.createObjectNode(),
        request.scopeMode(),
        request.toolProfile(),
        traceId,
        request.sessionId()
    );

    Flux<SseEnvelope> result = toolInvoker.call(callRequest)
        .map(response -> {
          if (response.ok()) {
            ObjectNode summary = objectMapper.createObjectNode();
            summary.put("toolName", toolName);
            summary.put("success", true);
            if (response.cacheHit() != null) {
              summary.put("cacheHit", response.cacheHit());
            }
            if (response.ttlHintSeconds() != null) {
              summary.put("ttlHintSeconds", response.ttlHintSeconds());
            }
            return createEnvelope(
                StageNames.TOOL_CALL_RESULT,
                "Tool call succeeded",
                summary,
                seq,
                traceId,
                request.sessionId()
            );
          }
          return createEnvelope(
              StageNames.TOOL_CALL_ERROR,
              "Tool call failed: " + response.error().message(),
              Map.of(
                  "toolName", toolName,
                  "error", response.error()
              ),
              seq,
              traceId,
              request.sessionId()
          );
        })
        .flux();

    return start.concatWith(result);
  }

  private Flux<SseEnvelope> simulateLlmStream(
      RagAnswerRequest request,
      String traceId,
      AtomicLong seq
  ) {
    String[] chunks = {"Hello ", "this ", "is ", "a ", "test ", "response."};
    return Flux.fromArray(chunks)
        .map(chunk -> createEnvelope(
            StageNames.ANSWER_DELTA,
            chunk,
            Map.of("delta", chunk),
            seq,
            traceId,
            request.sessionId()
        ));
  }

  private String extractDebugToolName(RagAnswerRequest request) {
    if (request.ext() != null && request.ext().containsKey("debugToolName")) {
      return request.ext().get("debugToolName").toString();
    }
    return null;
  }

  private SseEnvelope createEnvelope(
      String stage,
      String message,
      Object payload,
      AtomicLong seq,
      String traceId,
      String sessionId
  ) {
    return new SseEnvelope(
        stage,
        message,
        payload,
        seq.incrementAndGet(),
        System.currentTimeMillis(),
        traceId,
        sessionId
    );
  }

  private static String truncate(String s, int n) {
    if (s == null) return "";
    return s.length() <= n ? s : s.substring(0, n) + " ...[truncated]";
  }
}
