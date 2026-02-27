package com.mrpot.agent.tools.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ToolDefinition;
import com.mrpot.agent.tools.service.ToolContext;
import com.mrpot.agent.tools.service.ToolHandler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class SystemUuidToolHandler implements ToolHandler {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String name() { return "system.uuid"; }

    @Override
    public ToolDefinition definition() {
        ObjectNode inputSchema = mapper.createObjectNode();
        inputSchema.put("type", "object");
        ObjectNode properties = mapper.createObjectNode();
        ObjectNode countProp = mapper.createObjectNode();
        countProp.put("type", "integer");
        countProp.put("description", "Number of UUIDs to generate (default: 1, max: 10)");
        properties.set("count", countProp);
        inputSchema.set("properties", properties);

        return new ToolDefinition(
            "system.uuid",
            "Generate one or more random UUIDs",
            "1.0.0",
            inputSchema,
            mapper.createObjectNode(),
            null,
            0L
        );
    }

    @Override
    public CallToolResponse handle(JsonNode args, ToolContext ctx) {
        int count = args.path("count").isMissingNode()
            ? 1
            : Math.min(args.path("count").asInt(1), 10);

        ArrayNode uuids = mapper.createArrayNode();
        for (int i = 0; i < count; i++) {
            uuids.add(UUID.randomUUID().toString());
        }

        ObjectNode result = mapper.createObjectNode();
        result.set("uuids", uuids);
        result.put("count", count);

        return new CallToolResponse(true, name(), result, false, 0L, Instant.now(), null);
    }
}
