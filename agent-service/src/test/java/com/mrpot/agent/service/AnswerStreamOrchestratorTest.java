package com.mrpot.agent.service;

import com.mrpot.agent.common.api.RagAnswerRequest;
import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.api.ToolProfile;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ToolDefinition;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerStreamOrchestratorTest {

  @Test
  void stream_contains_tool_events_and_final() {
    ToolRegistryClient registryClient = Mockito.mock(ToolRegistryClient.class);
    ToolInvoker invoker = Mockito.mock(ToolInvoker.class);
    Mockito.when(registryClient.ensureFresh(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString()))
        .thenReturn(List.of(new ToolDefinition("system.ping", "d", "1", null, null, null, 1L)));
    Mockito.when(invoker.call(Mockito.any())).thenReturn(
        new CallToolResponse(true, "system.ping", new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("ok", true), false, 1L, Instant.now(), null));

    var orchestrator = new AnswerStreamOrchestrator(registryClient, invoker);
    ReflectionTestUtils.setField(orchestrator, "allowExplicitTool", true);

    var req = new RagAnswerRequest("q", "s", null, null, ScopeMode.AUTO, ToolProfile.DEFAULT, null, null,
        Map.of("debugToolName", "system.ping"));
    var list = orchestrator.stream(req, "trace").collectList().block();

    assertTrue(list.stream().anyMatch(e -> StageNames.TOOL_CALL_START.equals(e.stage())));
    assertTrue(list.stream().anyMatch(e -> StageNames.TOOL_CALL_RESULT.equals(e.stage())));
    assertEquals(StageNames.ANSWER_FINAL, list.get(list.size() - 1).stage());
  }
}
