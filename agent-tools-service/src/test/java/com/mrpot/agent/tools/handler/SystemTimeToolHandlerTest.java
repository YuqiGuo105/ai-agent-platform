package com.mrpot.agent.tools.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrpot.agent.common.tool.mcp.ToolError;
import com.mrpot.agent.tools.service.ToolContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SystemTimeToolHandlerTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final SystemTimeToolHandler handler = new SystemTimeToolHandler();

  @Test
  void handle_valid_timezone() {
    var args = mapper.createObjectNode().put("tz", "UTC");
    var response = handler.handle(args, new ToolContext("t", "s"));
    assertTrue(response.ok());
    assertEquals("UTC", response.result().get("tz").asText());
  }

  @Test
  void handle_invalid_timezone() {
    var args = mapper.createObjectNode().put("tz", "NO_SUCH_TZ");
    var response = handler.handle(args, new ToolContext("t", "s"));
    assertFalse(response.ok());
    assertEquals(ToolError.BAD_ARGS, response.error().code());
  }
}
