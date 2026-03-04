package com.mrpot.agent.tools.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ToolDefinition;
import com.mrpot.agent.common.tool.mcp.ToolError;
import com.mrpot.agent.tools.service.ToolContext;
import com.mrpot.agent.tools.service.ToolHandler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * JSON utility tool.
 * Provides JSON parsing, path extraction, transformation, and validation.
 */
@Component
public class JsonTool implements ToolHandler {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String name() {
        return "json.parse";
    }

    @Override
    public ToolDefinition definition() {
        ObjectNode inputSchema = mapper.createObjectNode();
        inputSchema.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();

        // operation
        ObjectNode operationProp = mapper.createObjectNode();
        operationProp.put("type", "string");
        operationProp.put("description", "Operation: 'parse' (parse JSON string), 'extract' (extract path), 'keys' (get keys), 'values' (get values), 'flatten' (flatten nested), 'validate' (validate JSON)");
        properties.set("operation", operationProp);

        // json - the input JSON
        ObjectNode jsonProp = mapper.createObjectNode();
        jsonProp.put("type", "string");
        jsonProp.put("description", "JSON string to process");
        properties.set("json", jsonProp);

        // path - JSON path for extraction
        ObjectNode pathProp = mapper.createObjectNode();
        pathProp.put("type", "string");
        pathProp.put("description", "Dot-notation path for extraction (e.g., 'data.items.0.name')");
        properties.set("path", pathProp);

        inputSchema.set("properties", properties);
        inputSchema.set("required", mapper.createArrayNode().add("operation").add("json"));

        ObjectNode outputSchema = mapper.createObjectNode();
        outputSchema.put("type", "object");

        return new ToolDefinition(
                name(),
                "Parse, extract, transform, and validate JSON data",
                "1.0.0",
                inputSchema,
                outputSchema,
                null,
                null
        );
    }

    @Override
    public CallToolResponse handle(JsonNode args, ToolContext ctx) {
        try {
            String operation = args.path("operation").asText("parse");
            String jsonStr = args.path("json").asText("");

            if (jsonStr.isEmpty()) {
                return errorResponse("json input is required");
            }

            return switch (operation.toLowerCase()) {
                case "parse" -> handleParse(jsonStr);
                case "extract" -> handleExtract(jsonStr, args.path("path").asText(""));
                case "keys" -> handleKeys(jsonStr);
                case "values" -> handleValues(jsonStr);
                case "flatten" -> handleFlatten(jsonStr);
                case "validate" -> handleValidate(jsonStr);
                default -> errorResponse("Unknown operation: " + operation);
            };
        } catch (Exception e) {
            return errorResponse("Error: " + e.getMessage());
        }
    }

    private CallToolResponse handleParse(String jsonStr) {
        try {
            JsonNode parsed = mapper.readTree(jsonStr);
            
            ObjectNode result = mapper.createObjectNode();
            result.set("parsed", parsed);
            result.put("type", getJsonType(parsed));
            
            if (parsed.isArray()) {
                result.put("length", parsed.size());
            } else if (parsed.isObject()) {
                result.put("fieldCount", parsed.size());
            }
            
            return new CallToolResponse(true, name(), result, false, null, Instant.now(), null);
        } catch (Exception e) {
            return errorResponse("Invalid JSON: " + e.getMessage());
        }
    }

    private CallToolResponse handleExtract(String jsonStr, String path) {
        try {
            if (path.isEmpty()) {
                return errorResponse("path is required for extract operation");
            }

            JsonNode root = mapper.readTree(jsonStr);
            JsonNode extracted = extractPath(root, path);

            ObjectNode result = mapper.createObjectNode();
            result.put("path", path);
            result.set("value", extracted);
            result.put("found", !extracted.isMissingNode());
            result.put("type", getJsonType(extracted));

            return new CallToolResponse(true, name(), result, false, null, Instant.now(), null);
        } catch (Exception e) {
            return errorResponse("Extraction error: " + e.getMessage());
        }
    }

    private CallToolResponse handleKeys(String jsonStr) {
        try {
            JsonNode root = mapper.readTree(jsonStr);
            
            if (!root.isObject()) {
                return errorResponse("keys operation requires a JSON object");
            }

            ArrayNode keysArray = mapper.createArrayNode();
            root.fieldNames().forEachRemaining(keysArray::add);

            ObjectNode result = mapper.createObjectNode();
            result.set("keys", keysArray);
            result.put("count", keysArray.size());

            return new CallToolResponse(true, name(), result, false, null, Instant.now(), null);
        } catch (Exception e) {
            return errorResponse("Keys extraction error: " + e.getMessage());
        }
    }

    private CallToolResponse handleValues(String jsonStr) {
        try {
            JsonNode root = mapper.readTree(jsonStr);
            
            if (!root.isObject()) {
                return errorResponse("values operation requires a JSON object");
            }

            ArrayNode valuesArray = mapper.createArrayNode();
            root.forEach(valuesArray::add);

            ObjectNode result = mapper.createObjectNode();
            result.set("values", valuesArray);
            result.put("count", valuesArray.size());

            return new CallToolResponse(true, name(), result, false, null, Instant.now(), null);
        } catch (Exception e) {
            return errorResponse("Values extraction error: " + e.getMessage());
        }
    }

    private CallToolResponse handleFlatten(String jsonStr) {
        try {
            JsonNode root = mapper.readTree(jsonStr);
            ObjectNode flattened = mapper.createObjectNode();
            flattenNode("", root, flattened);

            ObjectNode result = mapper.createObjectNode();
            result.set("flattened", flattened);
            result.put("fieldCount", flattened.size());

            return new CallToolResponse(true, name(), result, false, null, Instant.now(), null);
        } catch (Exception e) {
            return errorResponse("Flatten error: " + e.getMessage());
        }
    }

    private CallToolResponse handleValidate(String jsonStr) {
        ObjectNode result = mapper.createObjectNode();
        
        try {
            JsonNode parsed = mapper.readTree(jsonStr);
            result.put("valid", true);
            result.put("type", getJsonType(parsed));
            
            if (parsed.isArray()) {
                result.put("length", parsed.size());
            } else if (parsed.isObject()) {
                result.put("fieldCount", parsed.size());
            }
        } catch (Exception e) {
            result.put("valid", false);
            result.put("error", e.getMessage());
        }

        return new CallToolResponse(true, name(), result, false, null, Instant.now(), null);
    }

    private JsonNode extractPath(JsonNode node, String path) {
        String[] parts = path.split("\\.");
        JsonNode current = node;

        for (String part : parts) {
            if (current.isMissingNode() || current.isNull()) {
                return mapper.missingNode();
            }

            // Check if it's an array index
            if (part.matches("\\d+")) {
                int index = Integer.parseInt(part);
                if (current.isArray() && index < current.size()) {
                    current = current.get(index);
                } else {
                    return mapper.missingNode();
                }
            } else {
                current = current.path(part);
            }
        }

        return current;
    }

    private void flattenNode(String prefix, JsonNode node, ObjectNode result) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String newPrefix = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
                flattenNode(newPrefix, entry.getValue(), result);
            });
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                String newPrefix = prefix.isEmpty() ? String.valueOf(i) : prefix + "." + i;
                flattenNode(newPrefix, node.get(i), result);
            }
        } else {
            result.set(prefix, node);
        }
    }

    private String getJsonType(JsonNode node) {
        if (node.isObject()) return "object";
        if (node.isArray()) return "array";
        if (node.isTextual()) return "string";
        if (node.isNumber()) return "number";
        if (node.isBoolean()) return "boolean";
        if (node.isNull()) return "null";
        if (node.isMissingNode()) return "missing";
        return "unknown";
    }

    private CallToolResponse errorResponse(String message) {
        return new CallToolResponse(
                false,
                name(),
                null,
                false,
                null,
                Instant.now(),
                new ToolError(ToolError.BAD_ARGS, message, false)
        );
    }
}
