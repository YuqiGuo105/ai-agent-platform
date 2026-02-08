package com.mrpot.agent.common.tool;

import com.fasterxml.jackson.databind.JsonNode;

public record ToolSchema(
    String schemaType,
    JsonNode schema
) {}
