package com.mrpot.agent.common;

import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.api.ToolProfile;
import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ListToolsRequest;
import com.mrpot.agent.common.tool.mcp.ToolError;
import com.mrpot.agent.common.ui.TableBlock;
import com.mrpot.agent.common.util.Json;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
  void mcp_request_and_error_response_roundtrip() throws Exception {
    var req = new ListToolsRequest(ScopeMode.GENERAL, ToolProfile.BASIC, "t-1", "s-1");
    var reqJson = Json.MAPPER.writeValueAsString(req);
    assertTrue(reqJson.contains("GENERAL"));

    var resp = new CallToolResponse(
        false,
        "system.time",
        null,
        false,
        0L,
        Instant.parse("2026-02-08T07:11:12Z"),
        new ToolError("TIMEOUT", "tool call timeout", true)
    );
    var respJson = Json.MAPPER.writeValueAsString(resp);
    assertTrue(respJson.contains("TIMEOUT"));
  }

  @Test
  void sse_tool_stage_names_exposed() {
    assertEquals("tool_call_start", StageNames.TOOL_CALL_START);
    assertEquals("tool_call_result", StageNames.TOOL_CALL_RESULT);
    assertEquals("tool_call_error", StageNames.TOOL_CALL_ERROR);
  }
}
