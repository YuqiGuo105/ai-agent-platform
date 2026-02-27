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
public class SystemEchoToolHandler implements ToolHandler {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String name() { return "system.echo"; }

    @Override
    public ToolDefinition definition() {
        ObjectNode inputSchema = mapper.createObjectNode();
        inputSchema.put("type", "object");
        ObjectNode properties = mapper.createObjectNode();
        ObjectNode msgProp = mapper.createObjectNode();
        msgProp.put("type", "string");
        msgProp.put("description", "Any message or payload to echo back");
        properties.set("message", msgProp);
        inputSchema.set("properties", properties);

        return new ToolDefinition(
            "system.echo",
            "Echo the input arguments back — useful for pipeline testing and tracing",
            "1.0.0",
            inputSchema,
            mapper.createObjectNode(),
            null,
            0L
        );
    }

    @Override
    public CallToolResponse handle(JsonNode args, ToolContext ctx) {
        ObjectNode result = mapper.createObjectNode();
        result.set("echo",      args);
        result.put("traceId",   ctx.traceId()   != null ? ctx.traceId()   : "");
        result.put("sessionId", ctx.sessionId() != null ? ctx.sessionId() : "");
        result.put("timestamp", Instant.now().toString());

        return new CallToolResponse(true, name(), result, false, 0L, Instant.now(), null);
    }
}
