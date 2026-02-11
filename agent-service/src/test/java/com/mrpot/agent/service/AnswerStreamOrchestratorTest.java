package com.mrpot.agent.service;

import com.mrpot.agent.common.api.RagAnswerRequest;
import com.mrpot.agent.common.api.RagOptions;
import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.api.ToolProfile;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ToolError;
import com.mrpot.agent.service.model.ChatMessage;
import com.mrpot.agent.service.telemetry.RunLogPublisher;
import com.mrpot.agent.service.telemetry.extractor.ExtractorRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class AnswerStreamOrchestratorTest {

  private ToolRegistryClient registryClient;
  private ToolInvoker toolInvoker;
  private RagAnswerService ragAnswerService;
  private LlmService llmService;
  private ConversationHistoryService conversationHistoryService;
  private RunLogPublisher publisher;
  private ExtractorRegistry extractorRegistry;
  private AnswerStreamOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    registryClient = Mockito.mock(ToolRegistryClient.class);
    toolInvoker = Mockito.mock(ToolInvoker.class);
    ragAnswerService = Mockito.mock(RagAnswerService.class);
    llmService = Mockito.mock(LlmService.class);
    conversationHistoryService = Mockito.mock(ConversationHistoryService.class);
    publisher = Mockito.mock(RunLogPublisher.class);
    extractorRegistry = Mockito.mock(ExtractorRegistry.class);

    // Default mocks
    when(registryClient.ensureFresh(any(), any(), any(), any()))
        .thenReturn(Mono.just(List.of()));
    when(ragAnswerService.generateFileExtractionEvents(any(), any(), any(), any()))
        .thenReturn(Flux.empty());
    when(ragAnswerService.extractFilesMono(any()))
        .thenReturn(Mono.just(Collections.emptyList()));
    when(ragAnswerService.fuseFilesIntoPrompt(any(), anyString()))
        .thenReturn("test prompt");
    when(conversationHistoryService.getRecentHistory(anyString(), anyInt()))
        .thenReturn(Mono.just(Collections.emptyList()));
    when(conversationHistoryService.saveConversationPair(anyString(), anyString(), anyString()))
        .thenReturn(Mono.empty());
    when(llmService.streamResponse(anyString(), any()))
        .thenReturn(Flux.just("Hello ", "world!"));

    orchestrator = new AnswerStreamOrchestrator(
        registryClient, toolInvoker, ragAnswerService, llmService,
        conversationHistoryService, publisher, extractorRegistry
    );
    ReflectionTestUtils.setField(orchestrator, "allowExplicitTool", false);
  }

  @Test
  void stream_emits_answer_events_without_tool_call() {
    RagAnswerRequest request = new RagAnswerRequest(
        "q",
        "s1",
        null,
        List.of(),
        ScopeMode.AUTO,
        ToolProfile.BASIC,
        null,
        new RagOptions(null, null, null, null, null),
        null
    );

    List<String> stages = orchestrator.stream(request, "trace1")
        .map(env -> env.stage())
        .collectList()
        .block();

    assertTrue(stages.contains(StageNames.START));
    assertTrue(stages.contains(StageNames.REDIS));
    assertTrue(stages.contains(StageNames.ANSWER_DELTA));
    assertTrue(stages.contains(StageNames.ANSWER_FINAL));
  }

  @Test
  void stream_includes_conversation_history_from_redis() {
    List<ChatMessage> history = List.of(
        ChatMessage.user("previous question"),
        ChatMessage.assistant("previous answer")
    );
    when(conversationHistoryService.getRecentHistory(anyString(), anyInt()))
        .thenReturn(Mono.just(history));

    RagAnswerRequest request = new RagAnswerRequest(
        "new question",
        "session1",
        null,
        List.of(),
        ScopeMode.AUTO,
        ToolProfile.BASIC,
        null,
        new RagOptions(null, null, null, null, null),
        null
    );

    List<String> stages = orchestrator.stream(request, "trace1")
        .map(env -> env.stage())
        .collectList()
        .block();

    assertTrue(stages.contains(StageNames.REDIS));
    Mockito.verify(conversationHistoryService).getRecentHistory("session1", 3);
  }

  @Test
  void stream_emits_tool_error_and_final_answer() {
    ReflectionTestUtils.setField(orchestrator, "allowExplicitTool", true);
    
    when(toolInvoker.call(any()))
        .thenReturn(Mono.just(new CallToolResponse(
            false, "system.ping", null, false, null, Instant.now(),
            new ToolError(ToolError.INTERNAL, "boom", false)
        )));

    RagAnswerRequest request = new RagAnswerRequest(
        "q",
        "s1",
        null,
        List.of(),
        ScopeMode.AUTO,
        ToolProfile.BASIC,
        null,
        new RagOptions(null, null, null, null, null),
        Map.of("debugToolName", "system.ping")
    );

    List<String> stages = orchestrator.stream(request, "trace1")
        .map(env -> env.stage())
        .collectList()
        .block();

    assertTrue(stages.contains(StageNames.TOOL_CALL_START));
    assertTrue(stages.contains(StageNames.TOOL_CALL_ERROR));
    assertTrue(stages.contains(StageNames.ANSWER_FINAL));
  }

  @Test
  void stream_saves_conversation_to_redis_after_completion() {
    RagAnswerRequest request = new RagAnswerRequest(
        "test question",
        "session1",
        null,
        List.of(),
        ScopeMode.AUTO,
        ToolProfile.BASIC,
        null,
        new RagOptions(null, null, null, null, null),
        null
    );

    orchestrator.stream(request, "trace1")
        .collectList()
        .block();

    Mockito.verify(conversationHistoryService).saveConversationPair(
        Mockito.eq("session1"),
        Mockito.eq("test question"),
        anyString()
    );
  }
}
