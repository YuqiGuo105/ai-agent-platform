package com.mrpot.agent.tools.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrpot.agent.common.kb.KbSearchRequest;
import com.mrpot.agent.common.kb.KbSearchResponse;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ToolDefinition;
import com.mrpot.agent.common.tool.mcp.ToolError;
import com.mrpot.agent.tools.client.KbServiceClient;
import com.mrpot.agent.tools.service.ToolContext;
import com.mrpot.agent.tools.service.ToolHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * MCP tool for searching the knowledge base using semantic similarity.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KbSearchTool implements ToolHandler {

    private static final ObjectMapper mapper = new ObjectMapper();
    private final KbServiceClient kbServiceClient;

    @Override
    public String name() {
        return "kb.search";
    }

    @Override
    public ToolDefinition definition() {
        ObjectNode inputSchema = mapper.createObjectNode();
        inputSchema.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();

        // query - required
        ObjectNode queryProp = mapper.createObjectNode();
        queryProp.put("type", "string");
        queryProp.put("description", "Search query text for semantic similarity matching");
        properties.set("query", queryProp);

        // topK - optional
        ObjectNode topKProp = mapper.createObjectNode();
        topKProp.put("type", "integer");
        topKProp.put("description", "Maximum number of documents to return (default: 5)");
        properties.set("topK", topKProp);

        // minScore - optional
        ObjectNode minScoreProp = mapper.createObjectNode();
        minScoreProp.put("type", "number");
        minScoreProp.put("description", "Minimum similarity score threshold (0.0-1.0, default: 0.7)");
        properties.set("minScore", minScoreProp);

        // filters - optional
        ObjectNode filtersProp = mapper.createObjectNode();
        filtersProp.put("type", "object");
        filtersProp.put("description", "Optional metadata filters for document selection");
        properties.set("filters", filtersProp);

        inputSchema.set("properties", properties);
        inputSchema.set("required", mapper.createArrayNode().add("query"));

        ObjectNode outputSchema = mapper.createObjectNode();
        outputSchema.put("type", "object");
        ObjectNode outputProps = mapper.createObjectNode();

        ObjectNode docsProp = mapper.createObjectNode();
        docsProp.put("type", "array");
        docsProp.put("description", "List of matching documents");
        outputProps.set("docs", docsProp);

        ObjectNode hitsProp = mapper.createObjectNode();
        hitsProp.put("type", "array");
        hitsProp.put("description", "List of document IDs with scores");
        outputProps.set("hits", hitsProp);

        ObjectNode contextProp = mapper.createObjectNode();
        contextProp.put("type", "string");
        contextProp.put("description", "Combined context text from all matching documents");
        outputProps.set("contextText", contextProp);

        outputSchema.set("properties", outputProps);

        return new ToolDefinition(
                name(),
                "Search the knowledge base for relevant documents using semantic similarity",
                "1.0.0",
                inputSchema,
                outputSchema,
                null,
                30L
        );
    }

    @Override
    public CallToolResponse handle(JsonNode args, ToolContext ctx) {
        // Extract and validate required query parameter
        String query = args.path("query").asText();
        if (query == null || query.isBlank()) {
            return new CallToolResponse(
                    false,
                    name(),
                    null,
                    false,
                    null,
                    Instant.now(),
                    new ToolError(
                            ToolError.BAD_ARGS,
                            "query is required and must not be blank",
                            false
                    )
            );
        }

        // Extract optional parameters (check for both missing and null nodes)
        JsonNode topKNode = args.path("topK");
        Integer topK = (topKNode.isMissingNode() || topKNode.isNull()) ? null : topKNode.asInt();
        JsonNode minScoreNode = args.path("minScore");
        Double minScore = (minScoreNode.isMissingNode() || minScoreNode.isNull()) ? null : minScoreNode.asDouble();
        
        Map<String, Object> filters = null;
        if (!args.path("filters").isMissingNode()) {
            try {
                filters = mapper.convertValue(args.get("filters"), Map.class);
            } catch (Exception e) {
                log.warn("Failed to parse filters: {}", e.getMessage());
            }
        }

        try {
            log.info("Executing KB search for query: '{}', topK: {}, minScore: {}", query, topK, minScore);

            // Create search request
            KbSearchRequest request = new KbSearchRequest(query, topK, minScore, filters);

            // Call KB service
            KbSearchResponse response = kbServiceClient.search(request);

            // Convert response to JSON
            JsonNode result = mapper.valueToTree(response);

            log.info("KB search returned {} documents, {} hits", 
                    response.docs().size(), response.hits().size());

            return new CallToolResponse(
                    true,
                    name(),
                    result,
                    false,
                    30L,
                    Instant.now(),
                    null
            );

        } catch (Exception e) {
            log.error("Error executing KB search: {}", e.getMessage(), e);

            ObjectNode errorResult = mapper.createObjectNode();
            errorResult.put("error", e.getMessage());
            errorResult.set("docs", mapper.createArrayNode());
            errorResult.set("hits", mapper.createArrayNode());
            errorResult.put("contextText", "");

            return new CallToolResponse(
                    false,
                    name(),
                    errorResult,
                    false,
                    null,
                    Instant.now(),
                    new ToolError(
                            ToolError.INTERNAL,
                            e.getMessage(),
                            false
                    )
            );
        }
    }
}
