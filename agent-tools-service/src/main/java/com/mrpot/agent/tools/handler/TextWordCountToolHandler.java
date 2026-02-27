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

@Component
public class TextWordCountToolHandler implements ToolHandler {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String name() { return "text.wordCount"; }

    @Override
    public ToolDefinition definition() {
        ObjectNode inputSchema = mapper.createObjectNode();
        inputSchema.put("type", "object");
        ObjectNode properties = mapper.createObjectNode();
        ObjectNode textProp = mapper.createObjectNode();
        textProp.put("type", "string");
        textProp.put("description", "Text to count words/characters in");
        properties.set("text", textProp);
        inputSchema.set("properties", properties);
        inputSchema.set("required", mapper.createArrayNode().add("text"));

        return new ToolDefinition(
            "text.wordCount",
            "Count words, characters, sentences, and lines in a piece of text",
            "1.0.0",
            inputSchema,
            mapper.createObjectNode(),
            null,
            0L
        );
    }

    @Override
    public CallToolResponse handle(JsonNode args, ToolContext ctx) {
        String text = args.path("text").asText("").trim();
        if (text.isBlank()) {
            return new CallToolResponse(false, name(), null, false, null, Instant.now(),
                new ToolError(ToolError.BAD_ARGS, "text is required", false));
        }

        int charCount = text.length();
        int wordCount = text.isEmpty() ? 0 : text.split("\\s+").length;
        int sentCount = text.split("[.!?。！？]+").length;
        int lineCount = text.split("\\r?\\n").length;

        ObjectNode result = mapper.createObjectNode();
        result.put("characters", charCount);
        result.put("words",      wordCount);
        result.put("sentences",  sentCount);
        result.put("lines",      lineCount);

        return new CallToolResponse(true, name(), result, false, 0L, Instant.now(), null);
    }
}
