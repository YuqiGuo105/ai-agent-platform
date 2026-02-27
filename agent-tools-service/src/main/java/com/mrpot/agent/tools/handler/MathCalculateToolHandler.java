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

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.time.Instant;

@Component
public class MathCalculateToolHandler implements ToolHandler {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String name() { return "math.calculate"; }

    @Override
    public ToolDefinition definition() {
        ObjectNode inputSchema = mapper.createObjectNode();
        inputSchema.put("type", "object");
        ObjectNode properties = mapper.createObjectNode();
        ObjectNode exprProp = mapper.createObjectNode();
        exprProp.put("type", "string");
        exprProp.put("description", "Math expression to evaluate (e.g. '(12 + 8) * 3 / 4')");
        properties.set("expression", exprProp);
        inputSchema.set("properties", properties);
        inputSchema.set("required", mapper.createArrayNode().add("expression"));

        return new ToolDefinition(
            "math.calculate",
            "Evaluate a safe arithmetic expression and return the numeric result",
            "1.0.0",
            inputSchema,
            mapper.createObjectNode(),
            null,
            0L
        );
    }

    @Override
    public CallToolResponse handle(JsonNode args, ToolContext ctx) {
        String expr = args.path("expression").asText("").trim();
        if (expr.isBlank()) {
            return new CallToolResponse(false, name(), null, false, null, Instant.now(),
                new ToolError(ToolError.BAD_ARGS, "expression is required", false));
        }
        // Only allow digits, spaces, arithmetic operators, parentheses, and decimal points
        if (!expr.matches("[\\d\\s+\\-*/().%]+")) {
            return new CallToolResponse(false, name(), null, false, null, Instant.now(),
                new ToolError(ToolError.BAD_ARGS,
                    "Unsafe expression — only arithmetic operators allowed", false));
        }
        try {
            ScriptEngine engine = new ScriptEngineManager().getEngineByName("JavaScript");
            Object evalResult = engine.eval(expr);
            double value = Double.parseDouble(evalResult.toString());

            ObjectNode result = mapper.createObjectNode();
            result.put("expression", expr);
            result.put("result", value);
            return new CallToolResponse(true, name(), result, false, 0L, Instant.now(), null);
        } catch (Exception e) {
            return new CallToolResponse(false, name(), null, false, null, Instant.now(),
                new ToolError(ToolError.BAD_ARGS,
                    "Cannot evaluate: " + e.getMessage(), false));
        }
    }
}
