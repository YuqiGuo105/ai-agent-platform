package com.mrpot.agent.common;

import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ListToolsResponse;
import com.mrpot.agent.common.tool.mcp.ToolDefinition;
import com.mrpot.agent.common.tool.mcp.ToolError;
import com.mrpot.agent.common.ui.TableBlock;
import com.mrpot.agent.common.util.Json;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonContractTest {

  @Test
  void uiBlock_polymorphism_roundtrip() throws Exception {
    var b = new TableBlock("Prices", List.of("t", "price"), List.of(List.of(1, 2.3)), 123L);
    var json = Json.MAPPER.writeValueAsString(b);
    assertTrue(json.contains("\"type\":\"table\""));
    var obj = Json.MAPPER.readValue(json, com.mrpot.agent.common.ui.UiBlock.class);
    assertEquals("table", obj.type());
  }

  @Test
  void sse_envelope_min_fields_ok() throws Exception {
    var e = new SseEnvelope("answer_delta", "chunk", "hi", null, null, null, null);
    var json = Json.MAPPER.writeValueAsString(e);
    assertTrue(json.contains("\"stage\""));
  }

  @Test
  void list_tools_response_roundtrip() throws Exception {
    var tool = new ToolDefinition("test", "desc", "1.0", null, null, null, 300L);
    var response = new ListToolsResponse(List.of(tool), 300L, Instant.now());
    var json = Json.MAPPER.writeValueAsString(response);
    assertTrue(json.contains("\"tools\""));
    var obj = Json.MAPPER.readValue(json, ListToolsResponse.class);
    assertEquals(1, obj.tools().size());
  }

  @Test
  void call_tool_response_error_roundtrip() throws Exception {
    var error = new ToolError(ToolError.TIMEOUT, "timeout", true);
    var response = new CallToolResponse(false, "test", null, false, null, Instant.now(), error);
    var json = Json.MAPPER.writeValueAsString(response);
    assertTrue(json.contains("\"retryable\":true"));
    var obj = Json.MAPPER.readValue(json, CallToolResponse.class);
    assertFalse(obj.ok());
    assertEquals(ToolError.TIMEOUT, obj.error().code());
  }

  @Test
  void sse_tool_call_events_roundtrip() throws Exception {
    var e1 = new SseEnvelope(
        StageNames.TOOL_CALL_START,
        "calling",
        Map.of("tool", "ping"),
        1L,
        System.currentTimeMillis(),
        "trace1",
        "sess1"
    );
    var json = Json.MAPPER.writeValueAsString(e1);
    assertTrue(json.contains("\"stage\":\"tool_call_start\""));
  }
}
