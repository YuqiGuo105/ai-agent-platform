package com.mrpot.agent.tools.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrpot.agent.tools.service.ToolContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SystemPingToolHandlerTest {
  private final SystemPingToolHandler handler = new SystemPingToolHandler();

  @Test
  void handle_returns_ok_result() {
    var response = handler.handle(new ObjectMapper().createObjectNode(), new ToolContext("t", "s"));
    assertTrue(response.ok());
    assertEquals("system.ping", response.toolName());
    assertTrue(response.result().get("ok").asBoolean());
  }
}
