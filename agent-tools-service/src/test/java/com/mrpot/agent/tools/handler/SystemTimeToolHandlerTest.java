package com.mrpot.agent.tools.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ToolError;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SystemTimeToolHandlerTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void time_returns_now_for_valid_tz() {
    SystemTimeToolHandler handler = new SystemTimeToolHandler();
    ObjectNode args = mapper.createObjectNode();
    args.put("tz", "UTC");

    CallToolResponse response = handler.handle(args, null);

    assertTrue(response.ok());
    assertEquals("UTC", response.result().get("tz").asText());
  }

  @Test
  void time_returns_error_for_invalid_tz() {
    SystemTimeToolHandler handler = new SystemTimeToolHandler();
    ObjectNode args = mapper.createObjectNode();
    args.put("tz", "Bad/Zone");

    CallToolResponse response = handler.handle(args, null);

    assertFalse(response.ok());
    assertEquals(ToolError.BAD_ARGS, response.error().code());
  }
}
