package com.mrpot.agent.service;

import com.mrpot.agent.common.api.RagAnswerRequest;
import com.mrpot.agent.common.api.RagOptions;
import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.api.ToolProfile;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ToolError;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerStreamOrchestratorTest {

  @Test
  void stream_emits_answer_events_without_tool_call() {
    ToolRegistryClient registryClient = org.mockito.Mockito.mock(ToolRegistryClient.class);
    org.mockito.Mockito.when(registryClient.ensureFresh(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(Mono.just(List.of()));
    ToolInvoker toolInvoker = org.mockito.Mockito.mock(ToolInvoker.class);

    AnswerStreamOrchestrator orchestrator = new AnswerStreamOrchestrator(registryClient, toolInvoker);
    ReflectionTestUtils.setField(orchestrator, "allowExplicitTool", false);

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
    assertTrue(stages.contains(StageNames.ANSWER_DELTA));
    assertTrue(stages.contains(StageNames.ANSWER_FINAL));
  }

  @Test
  void stream_emits_tool_error_and_final_answer() {
    ToolRegistryClient registryClient = org.mockito.Mockito.mock(ToolRegistryClient.class);
    org.mockito.Mockito.when(registryClient.ensureFresh(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(Mono.just(List.of()));
    ToolInvoker toolInvoker = org.mockito.Mockito.mock(ToolInvoker.class);
    org.mockito.Mockito.when(toolInvoker.call(org.mockito.ArgumentMatchers.any()))
        .thenReturn(Mono.just(new CallToolResponse(false, "system.ping", null, false, null, Instant.now(), new ToolError(ToolError.INTERNAL, "boom", false))));
    AnswerStreamOrchestrator orchestrator = new AnswerStreamOrchestrator(registryClient, toolInvoker);
    ReflectionTestUtils.setField(orchestrator, "allowExplicitTool", true);

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
}
