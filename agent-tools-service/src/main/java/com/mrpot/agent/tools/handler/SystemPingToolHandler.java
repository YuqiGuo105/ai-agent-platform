package com.mrpot.agent.tools.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ToolDefinition;
import com.mrpot.agent.tools.service.ToolContext;
import com.mrpot.agent.tools.service.ToolHandler;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SystemPingToolHandler implements ToolHandler {
  private static final ObjectMapper mapper = new ObjectMapper();

  @Override
  public String name() {
    return "system.ping";
  }

  @Override
  public ToolDefinition definition() {
    return new ToolDefinition(
        "system.ping",
        "Simple ping tool for health check",
        "1.0.0",
        mapper.createObjectNode(),
        mapper.createObjectNode(),
        null,
        300L
    );
  }

  @Override
  public CallToolResponse handle(JsonNode args, ToolContext ctx) {
    ObjectNode result = mapper.createObjectNode();
    result.put("ok", true);
    result.put("timestamp", Instant.now().toString());

    return new CallToolResponse(
        true,
        name(),
        result,
        false,
        300L,
        Instant.now(),
        null
    );
  }
}
