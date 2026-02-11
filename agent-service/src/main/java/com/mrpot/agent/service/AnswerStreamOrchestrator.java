package com.mrpot.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrpot.agent.common.api.RagAnswerRequest;
import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.common.telemetry.RunLogEnvelope;
import com.mrpot.agent.common.tool.FileItem;
import com.mrpot.agent.common.tool.mcp.CallToolRequest;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.service.model.ChatMessage;
import com.mrpot.agent.service.telemetry.RunLogPublisher;
import com.mrpot.agent.service.telemetry.extractor.ExtractorRegistry;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class AnswerStreamOrchestrator {
  private final ToolRegistryClient registryClient;
  private final ToolInvoker toolInvoker;
  private final RagAnswerService ragAnswerService;
  private final LlmService llmService;
  private final ConversationHistoryService conversationHistoryService;
  private final RunLogPublisher publisher;
  private final ExtractorRegistry extractorRegistry;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${mcp.debug.allow-explicit-tool:false}")
  private boolean allowExplicitTool;

  public AnswerStreamOrchestrator(
      ToolRegistryClient registryClient,
      ToolInvoker toolInvoker,
      RagAnswerService ragAnswerService,
      LlmService llmService,
      ConversationHistoryService conversationHistoryService,
      RunLogPublisher publisher,
      ExtractorRegistry extractorRegistry
  ) {
    this.registryClient = registryClient;
    this.toolInvoker = toolInvoker;
    this.ragAnswerService = ragAnswerService;
    this.llmService = llmService;
    this.conversationHistoryService = conversationHistoryService;
    this.publisher = publisher;
    this.extractorRegistry = extractorRegistry;
  }

  public Flux<SseEnvelope> stream(RagAnswerRequest request, String traceId) {
    AtomicLong seq = new AtomicLong(0);
    String runId = UUID.randomUUID().toString();
    long t0 = System.currentTimeMillis();

    // Shared state for final answer
    AtomicReference<String> finalAnswerRef = new AtomicReference<>("");
    AtomicReference<List<ChatMessage>> historyRef = new AtomicReference<>(Collections.emptyList());
    AtomicReference<List<FileItem>> extractedFilesRef = new AtomicReference<>(Collections.emptyList());

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

    // History retrieval stage - retrieve conversation history from Redis
    Flux<SseEnvelope> historyFlux = conversationHistoryService.getRecentHistory(request.sessionId(), 3)
        .map(history -> {
          historyRef.set(history);
          Instant oldestTs = history.stream()
              .map(ChatMessage::timestamp)
              .filter(t -> t != null)
              .min(Instant::compareTo)
              .orElse(null);
          return createEnvelope(
              StageNames.REDIS,
              "Conversation history retrieved",
              Map.of(
                  "historyCount", history.size(),
                  "oldestMessageTimestamp", oldestTs != null ? oldestTs.toString() : "null",
                  "sessionId", request.sessionId()
              ),
              seq,
              traceId,
              request.sessionId()
          );
        })
        .onErrorResume(e -> {
          historyRef.set(Collections.emptyList());
          return Mono.just(createEnvelope(
              StageNames.REDIS,
              "History retrieval failed (continuing without history)",
              Map.of("historyCount", 0, "error", e.getMessage() != null ? e.getMessage() : "Unknown"),
              seq,
              traceId,
              request.sessionId()
          ));
        })
        .flux();

    Mono<Void> ensureFresh = registryClient.ensureFresh(
        request.scopeMode(),
        request.toolProfile(),
        traceId,
        request.sessionId()
    ).then();

    // File extraction flow - store extracted files for prompt building
    List<String> fileUrls = request.resolveFileUrls(3);
    Flux<SseEnvelope> fileExtractFlux = ragAnswerService.extractFilesMono(fileUrls)
        .doOnNext(extractedFilesRef::set)
        .thenMany(ragAnswerService.generateFileExtractionEvents(
            fileUrls,
            traceId,
            request.sessionId(),
            seq
        ));

    Flux<SseEnvelope> toolCallFlux = Mono.fromSupplier(() -> extractDebugToolName(request))
        .filter(name -> name != null && allowExplicitTool)
        .flatMapMany(name -> executeToolCall(name, request, traceId, seq));

    // LLM streaming with actual DeepSeek integration
    Flux<SseEnvelope> answerFlux = Flux.defer(() -> {
      // Build prompt with file context
      String filePrompt = ragAnswerService.fuseFilesIntoPrompt(extractedFilesRef.get(), request.question());
      
      // Stream LLM response with conversation history
      StringBuilder answer = new StringBuilder();
      return llmService.streamResponse(filePrompt, historyRef.get())
          .map(chunk -> {
            answer.append(chunk);
            finalAnswerRef.set(answer.toString());
            return createEnvelope(
                StageNames.ANSWER_DELTA,
                chunk,
                Map.of("delta", chunk),
                seq,
                traceId,
                request.sessionId()
            );
          })
          .onErrorResume(e -> {
            // Fallback to simulated response on LLM error
            return simulateLlmStreamFallback(request, traceId, seq, finalAnswerRef);
          });
    });

    Flux<SseEnvelope> finalFlux = Flux.defer(() -> {
      long totalMs = System.currentTimeMillis() - t0;
      String finalAnswer = finalAnswerRef.get();
      
      // Save conversation to Redis (user question + assistant answer)
      return conversationHistoryService.saveConversationPair(
              request.sessionId(),
              request.question(),
              finalAnswer
          )
          .onErrorResume(e -> Mono.empty())  // Don't fail on save error
          .thenMany(Flux.defer(() -> {
            // Publish run.final telemetry event
            publisher.publish(new RunLogEnvelope(
                "1", "run.final", runId, traceId, request.sessionId(), "userId_placeholder",
                "GENERAL", "deepseek", Instant.now(),
                Map.of(
                    "answerFinal", truncate(finalAnswer, 11000),
                    "totalLatencyMs", totalMs
                )
            ));
            return Flux.just(createEnvelope(
                StageNames.ANSWER_FINAL,
                "Complete",
                Map.of("answer", finalAnswer),
                seq,
                traceId,
                request.sessionId()
            ));
          }));
    });

    return start
        .concatWith(historyFlux)
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
            
            // Extract key info from tool result using ExtractorRegistry
            Map<String, Object> keyInfo = extractorRegistry.extractFromResult(toolName, response.result());
            if (keyInfo != null && !keyInfo.isEmpty()) {
              summary.putPOJO("keyInfo", keyInfo);
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

  /**
   * Fallback LLM streaming for when actual LLM service fails.
   */
  private Flux<SseEnvelope> simulateLlmStreamFallback(
      RagAnswerRequest request,
      String traceId,
      AtomicLong seq,
      AtomicReference<String> finalAnswerRef
  ) {
    String[] chunks = {"I apologize, ", "but I'm ", "currently ", "unable ", "to ", "generate ", "a ", "response. ",
        "Please ", "try ", "again ", "later."};
    StringBuilder answer = new StringBuilder();
    return Flux.fromArray(chunks)
        .map(chunk -> {
          answer.append(chunk);
          finalAnswerRef.set(answer.toString());
          return createEnvelope(
              StageNames.ANSWER_DELTA,
              chunk,
              Map.of("delta", chunk),
              seq,
              traceId,
              request.sessionId()
          );
        });
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
