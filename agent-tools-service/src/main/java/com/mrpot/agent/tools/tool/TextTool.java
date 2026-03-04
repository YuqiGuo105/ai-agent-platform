package com.mrpot.agent.tools.tool;

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
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Text manipulation tool.
 * Provides operations for processing, transforming, and analyzing text.
 */
@Component
public class TextTool implements ToolHandler {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String name() {
        return "text.process";
    }

    @Override
    public ToolDefinition definition() {
        ObjectNode inputSchema = mapper.createObjectNode();
        inputSchema.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();

        // operation
        ObjectNode operationProp = mapper.createObjectNode();
        operationProp.put("type", "string");
        operationProp.put("description", "Operation: 'length', 'wordCount', 'upper', 'lower', 'trim', 'split', 'replace', 'contains', 'extract', 'truncate', 'reverse', 'stats'");
        properties.set("operation", operationProp);

        // text - the input text
        ObjectNode textProp = mapper.createObjectNode();
        textProp.put("type", "string");
        textProp.put("description", "Text to process");
        properties.set("text", textProp);

        // pattern - for regex operations
        ObjectNode patternProp = mapper.createObjectNode();
        patternProp.put("type", "string");
        patternProp.put("description", "Pattern for split, replace, or extract operations");
        properties.set("pattern", patternProp);

        // replacement - for replace operation
        ObjectNode replacementProp = mapper.createObjectNode();
        replacementProp.put("type", "string");
        replacementProp.put("description", "Replacement text for replace operation");
        properties.set("replacement", replacementProp);

        // maxLength - for truncate operation
        ObjectNode maxLengthProp = mapper.createObjectNode();
        maxLengthProp.put("type", "integer");
        maxLengthProp.put("description", "Maximum length for truncate operation");
        properties.set("maxLength", maxLengthProp);

        inputSchema.set("properties", properties);
        inputSchema.set("required", mapper.createArrayNode().add("operation").add("text"));

        ObjectNode outputSchema = mapper.createObjectNode();
        outputSchema.put("type", "object");

        return new ToolDefinition(
                name(),
                "Process, transform, and analyze text content",
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
            String operation = args.path("operation").asText("");
            String text = args.path("text").asText("");

            if (operation.isEmpty()) {
                return errorResponse("operation is required");
            }

            return switch (operation.toLowerCase()) {
                case "length" -> handleLength(text);
                case "wordcount" -> handleWordCount(text);
                case "upper" -> handleUpperCase(text);
                case "lower" -> handleLowerCase(text);
                case "trim" -> handleTrim(text);
                case "split" -> handleSplit(text, args.path("pattern").asText(" "));
                case "replace" -> handleReplace(text, args.path("pattern").asText(""), args.path("replacement").asText(""));
                case "contains" -> handleContains(text, args.path("pattern").asText(""));
                case "extract" -> handleExtract(text, args.path("pattern").asText(""));
                case "truncate" -> handleTruncate(text, args.path("maxLength").asInt(100));
                case "reverse" -> handleReverse(text);
                case "stats" -> handleStats(text);
                default -> errorResponse("Unknown operation: " + operation);
            };
        } catch (Exception e) {
            return errorResponse("Error: " + e.getMessage());
        }
    }

    private CallToolResponse handleLength(String text) {
        ObjectNode result = mapper.createObjectNode();
        result.put("length", text.length());
        result.put("text", text);
        return successResponse(result);
    }

    private CallToolResponse handleWordCount(String text) {
        String[] words = text.trim().split("\\s+");
        int count = text.trim().isEmpty() ? 0 : words.length;

        ObjectNode result = mapper.createObjectNode();
        result.put("wordCount", count);
        result.put("characterCount", text.length());
        result.put("characterCountNoSpaces", text.replaceAll("\\s", "").length());
        return successResponse(result);
    }

    private CallToolResponse handleUpperCase(String text) {
        ObjectNode result = mapper.createObjectNode();
        result.put("result", text.toUpperCase());
        result.put("original", text);
        return successResponse(result);
    }

    private CallToolResponse handleLowerCase(String text) {
        ObjectNode result = mapper.createObjectNode();
        result.put("result", text.toLowerCase());
        result.put("original", text);
        return successResponse(result);
    }

    private CallToolResponse handleTrim(String text) {
        String trimmed = text.trim();
        ObjectNode result = mapper.createObjectNode();
        result.put("result", trimmed);
        result.put("trimmedChars", text.length() - trimmed.length());
        return successResponse(result);
    }

    private CallToolResponse handleSplit(String text, String pattern) {
        try {
            String[] parts = text.split(Pattern.quote(pattern));
            
            ObjectNode result = mapper.createObjectNode();
            var partsArray = mapper.createArrayNode();
            for (String part : parts) {
                partsArray.add(part);
            }
            result.set("parts", partsArray);
            result.put("count", parts.length);
            result.put("delimiter", pattern);
            return successResponse(result);
        } catch (PatternSyntaxException e) {
            return errorResponse("Invalid pattern: " + e.getMessage());
        }
    }

    private CallToolResponse handleReplace(String text, String pattern, String replacement) {
        if (pattern.isEmpty()) {
            return errorResponse("pattern is required for replace operation");
        }

        try {
            String result = text.replace(pattern, replacement);
            int count = (text.length() - result.length() + replacement.length() * countOccurrences(text, pattern)) / 
                        (pattern.length() > 0 ? pattern.length() : 1);

            ObjectNode resultNode = mapper.createObjectNode();
            resultNode.put("result", result);
            resultNode.put("replacements", countOccurrences(text, pattern));
            resultNode.put("pattern", pattern);
            resultNode.put("replacement", replacement);
            return successResponse(resultNode);
        } catch (Exception e) {
            return errorResponse("Replace error: " + e.getMessage());
        }
    }

    private int countOccurrences(String text, String pattern) {
        if (pattern.isEmpty()) return 0;
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }

    private CallToolResponse handleContains(String text, String pattern) {
        if (pattern.isEmpty()) {
            return errorResponse("pattern is required for contains operation");
        }

        boolean contains = text.contains(pattern);
        int firstIndex = text.indexOf(pattern);

        ObjectNode result = mapper.createObjectNode();
        result.put("contains", contains);
        result.put("firstIndex", firstIndex);
        result.put("occurrences", countOccurrences(text, pattern));
        result.put("pattern", pattern);
        return successResponse(result);
    }

    private CallToolResponse handleExtract(String text, String pattern) {
        if (pattern.isEmpty()) {
            return errorResponse("pattern (regex) is required for extract operation");
        }

        try {
            Pattern regex = Pattern.compile(pattern);
            var matcher = regex.matcher(text);
            var matches = mapper.createArrayNode();

            while (matcher.find()) {
                ObjectNode match = mapper.createObjectNode();
                match.put("match", matcher.group());
                match.put("start", matcher.start());
                match.put("end", matcher.end());
                matches.add(match);
            }

            ObjectNode result = mapper.createObjectNode();
            result.set("matches", matches);
            result.put("matchCount", matches.size());
            result.put("pattern", pattern);
            return successResponse(result);
        } catch (PatternSyntaxException e) {
            return errorResponse("Invalid regex pattern: " + e.getMessage());
        }
    }

    private CallToolResponse handleTruncate(String text, int maxLength) {
        if (maxLength < 0) {
            return errorResponse("maxLength must be non-negative");
        }

        String truncated = text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
        
        ObjectNode result = mapper.createObjectNode();
        result.put("result", truncated);
        result.put("originalLength", text.length());
        result.put("truncated", text.length() > maxLength);
        return successResponse(result);
    }

    private CallToolResponse handleReverse(String text) {
        String reversed = new StringBuilder(text).reverse().toString();
        
        ObjectNode result = mapper.createObjectNode();
        result.put("result", reversed);
        result.put("original", text);
        return successResponse(result);
    }

    private CallToolResponse handleStats(String text) {
        ObjectNode result = mapper.createObjectNode();
        result.put("length", text.length());
        result.put("wordCount", text.trim().isEmpty() ? 0 : text.trim().split("\\s+").length);
        result.put("lineCount", text.split("\n", -1).length);
        result.put("sentenceCount", text.split("[.!?]+").length);
        result.put("digitCount", text.replaceAll("[^0-9]", "").length());
        result.put("letterCount", text.replaceAll("[^a-zA-Z]", "").length());
        result.put("whitespaceCount", text.replaceAll("[^\\s]", "").length());
        
        // Count unique words
        String[] words = text.toLowerCase().split("\\W+");
        Set<String> uniqueWords = new HashSet<>(Arrays.asList(words));
        uniqueWords.remove(""); // Remove empty string
        result.put("uniqueWordCount", uniqueWords.size());
        
        return successResponse(result);
    }

    private CallToolResponse successResponse(ObjectNode result) {
        return new CallToolResponse(true, name(), result, false, null, Instant.now(), null);
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
