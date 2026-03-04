package com.mrpot.agent.tools.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ToolDefinition;
import com.mrpot.agent.tools.service.ToolContext;
import com.mrpot.agent.tools.service.ToolHandler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * System health check tool.
 * Returns basic system information and confirms the tool service is operational.
 */
@Component
public class SystemPingTool implements ToolHandler {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    @Override
    public String name() {
        return "system.ping";
    }

    @Override
    public ToolDefinition definition() {
        ObjectNode inputSchema = mapper.createObjectNode();
        inputSchema.put("type", "object");
        inputSchema.set("properties", mapper.createObjectNode());
        inputSchema.set("required", mapper.createArrayNode());

        ObjectNode outputSchema = mapper.createObjectNode();
        outputSchema.put("type", "object");
        ObjectNode outputProps = mapper.createObjectNode();

        ObjectNode okProp = mapper.createObjectNode();
        okProp.put("type", "boolean");
        okProp.put("description", "Whether the system is healthy");
        outputProps.set("ok", okProp);

        ObjectNode timestampProp = mapper.createObjectNode();
        timestampProp.put("type", "string");
        timestampProp.put("description", "Current server timestamp in ISO format");
        outputProps.set("timestamp", timestampProp);

        ObjectNode versionProp = mapper.createObjectNode();
        versionProp.put("type", "string");
        versionProp.put("description", "Tool service version");
        outputProps.set("version", versionProp);

        outputSchema.set("properties", outputProps);

        return new ToolDefinition(
                name(),
                "Health check endpoint to verify tool service is operational",
                "1.0.0",
                inputSchema,
                outputSchema,
                null,
                60L  // Cache for 1 minute
        );
    }

    @Override
    public CallToolResponse handle(JsonNode args, ToolContext ctx) {
        ObjectNode result = mapper.createObjectNode();
        result.put("ok", true);
        result.put("timestamp", Instant.now().toString());
        result.put("version", "1.0.0");
        result.put("javaVersion", System.getProperty("java.version"));
        result.put("freeMemoryMB", Runtime.getRuntime().freeMemory() / (1024 * 1024));
        result.put("totalMemoryMB", Runtime.getRuntime().totalMemory() / (1024 * 1024));

        return new CallToolResponse(
                true,
                name(),
                result,
                true,  // cacheable
                60L,   // TTL 60 seconds
                Instant.now(),
                null
        );
    }
}
