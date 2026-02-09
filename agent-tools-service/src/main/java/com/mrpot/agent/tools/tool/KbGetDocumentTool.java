package com.mrpot.agent.tools.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrpot.agent.common.kb.KbDocument;
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

/**
 * MCP tool for retrieving a specific KB document by ID.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KbGetDocumentTool implements ToolHandler {

    private static final ObjectMapper mapper = new ObjectMapper();
    private final KbServiceClient kbServiceClient;

    @Override
    public String name() {
        return "kb.getDocument";
    }

    @Override
    public ToolDefinition definition() {
        ObjectNode inputSchema = mapper.createObjectNode();
        inputSchema.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();

        // id - required
        ObjectNode idProp = mapper.createObjectNode();
        idProp.put("type", "string");
        idProp.put("description", "Document ID to retrieve");
        properties.set("id", idProp);

        inputSchema.set("properties", properties);
        inputSchema.set("required", mapper.createArrayNode().add("id"));

        ObjectNode outputSchema = mapper.createObjectNode();
        outputSchema.put("type", "object");
        ObjectNode outputProps = mapper.createObjectNode();

        ObjectNode idOutProp = mapper.createObjectNode();
        idOutProp.put("type", "string");
        outputProps.set("id", idOutProp);

        ObjectNode docTypeProp = mapper.createObjectNode();
        docTypeProp.put("type", "string");
        outputProps.set("docType", docTypeProp);

        ObjectNode titleProp = mapper.createObjectNode();
        titleProp.put("type", "string");
        outputProps.set("title", titleProp);

        ObjectNode contentProp = mapper.createObjectNode();
        contentProp.put("type", "string");
        contentProp.put("description", "Full document content");
        outputProps.set("content", contentProp);

        ObjectNode metadataProp = mapper.createObjectNode();
        metadataProp.put("type", "object");
        metadataProp.put("description", "Document metadata");
        outputProps.set("metadata", metadataProp);

        outputSchema.set("properties", outputProps);

        return new ToolDefinition(
                name(),
                "Retrieve full content of a specific knowledge base document by ID",
                "1.0.0",
                inputSchema,
                outputSchema,
                null,
                10L
        );
    }

    @Override
    public CallToolResponse handle(JsonNode args, ToolContext ctx) {
        // Extract and validate required id parameter
        String id = args.path("id").asText();
        if (id == null || id.isBlank()) {
            return new CallToolResponse(
                    false,
                    name(),
                    null,
                    false,
                    null,
                    Instant.now(),
                    new ToolError(
                            ToolError.BAD_ARGS,
                            "id is required and must not be blank",
                            false
                    )
            );
        }

        try {
            log.info("Retrieving KB document with ID: {}", id);

            // Call KB service
            KbDocument document = kbServiceClient.getDocument(id);

            if (document == null) {
                return new CallToolResponse(
                        false,
                        name(),
                        null,
                        false,
                        null,
                        Instant.now(),
                        new ToolError(
                                ToolError.NOT_FOUND,
                                "Document with ID '" + id + "' not found",
                                false
                        )
                );
            }

            // Convert document to JSON
            JsonNode result = mapper.valueToTree(document);

            log.info("Successfully retrieved KB document: {}", id);

            return new CallToolResponse(
                    true,
                    name(),
                    result,
                    false,
                    10L,
                    Instant.now(),
                    null
            );

        } catch (Exception e) {
            log.error("Error retrieving KB document {}: {}", id, e.getMessage(), e);

            ObjectNode errorResult = mapper.createObjectNode();
            errorResult.put("error", e.getMessage());

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
