package com.mrpot.agent.tools.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemPingToolHandlerTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void ping_returns_ok_payload() {
    SystemPingToolHandler handler = new SystemPingToolHandler();
    CallToolResponse response = handler.handle(mapper.createObjectNode(), null);

    assertTrue(response.ok());
    assertNotNull(response.result());
    assertTrue(response.result().get("ok").asBoolean());
  }
}
