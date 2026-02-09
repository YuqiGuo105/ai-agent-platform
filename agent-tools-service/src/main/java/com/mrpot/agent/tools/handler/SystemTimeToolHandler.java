package com.mrpot.agent.tools.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ToolDefinition;
import com.mrpot.agent.common.tool.mcp.ToolError;
import com.mrpot.agent.tools.service.ToolContext;
import com.mrpot.agent.tools.service.ToolHandler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
public class SystemTimeToolHandler implements ToolHandler {
  private static final ObjectMapper mapper = new ObjectMapper();

  @Override
  public String name() {
    return "system.time";
  }

  @Override
  public ToolDefinition definition() {
    ObjectNode inputSchema = mapper.createObjectNode();
    inputSchema.put("type", "object");
    ObjectNode properties = mapper.createObjectNode();
    properties.set("tz", mapper.createObjectNode().put("type", "string"));
    inputSchema.set("properties", properties);

    return new ToolDefinition(
        "system.time",
        "Get current time in specified timezone",
        "1.0.0",
        inputSchema,
        mapper.createObjectNode(),
        null,
        60L
    );
  }

  @Override
  public CallToolResponse handle(JsonNode args, ToolContext ctx) {
    try {
      String tz = args.has("tz") ? args.get("tz").asText() : "UTC";
      ZonedDateTime now = ZonedDateTime.now(ZoneId.of(tz));

      ObjectNode result = mapper.createObjectNode();
      result.put("now", now.toString());
      result.put("tz", tz);

      return new CallToolResponse(
          true,
          name(),
          result,
          false,
          60L,
          Instant.now(),
          null
      );
    } catch (Exception e) {
      return new CallToolResponse(
          false,
          name(),
          null,
          false,
          null,
          Instant.now(),
          new ToolError(ToolError.BAD_ARGS, "Invalid timezone: " + e.getMessage(), false)
      );
    }
  }
}
