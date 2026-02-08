package com.mrpot.agent.tools.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ToolDefinition;
import com.mrpot.agent.common.tool.mcp.ToolError;
import com.mrpot.agent.tools.service.ToolContext;
import com.mrpot.agent.tools.service.ToolHandler;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Component;

@Component
public class SystemTimeToolHandler implements ToolHandler {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public String name() {
    return "system.time";
  }

  @Override
  public ToolDefinition definition() {
    ObjectNode inputSchema = MAPPER.createObjectNode();
    inputSchema.put("type", "object");
    ObjectNode properties = MAPPER.createObjectNode();
    properties.set("tz", MAPPER.createObjectNode().put("type", "string"));
    inputSchema.set("properties", properties);

    return new ToolDefinition(name(), "Get current time in specified timezone", "1.0.0", inputSchema,
        MAPPER.createObjectNode(), null, 60L);
  }

  @Override
  public CallToolResponse handle(JsonNode args, ToolContext ctx) {
    try {
      String tz = args != null && args.has("tz") ? args.get("tz").asText() : "UTC";
      ZonedDateTime now = ZonedDateTime.now(ZoneId.of(tz));
      ObjectNode result = MAPPER.createObjectNode();
      result.put("now", now.toString());
      result.put("tz", tz);
      return new CallToolResponse(true, name(), result, false, 60L, Instant.now(), null);
    } catch (Exception e) {
      return new CallToolResponse(false, name(), null, false, null, Instant.now(),
          new ToolError(ToolError.BAD_ARGS, "Invalid timezone: " + e.getMessage(), false));
    }
  }
}
