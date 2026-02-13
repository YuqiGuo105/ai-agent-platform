package com.mrpot.agent.tools.tool.deep;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Centralized JSON Schema definitions for Deep Tools.
 */
public final class DeepToolSchemas {
    
    private static final ObjectMapper mapper = new ObjectMapper();
    
    private DeepToolSchemas() {}
    
    // ============ Planning Tools ============
    
    /**
     * Input schema for planning.decompose tool.
     */
    public static ObjectNode planningDecomposeInput() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = mapper.createObjectNode();
        
        ObjectNode objective = mapper.createObjectNode();
        objective.put("type", "string");
        objective.put("description", "The main objective to decompose into subtasks");
        properties.set("objective", objective);
        
        ObjectNode context = mapper.createObjectNode();
        context.put("type", "string");
        context.put("description", "Additional context for decomposition");
        properties.set("context", context);
        
        schema.set("properties", properties);
        schema.set("required", mapper.createArrayNode().add("objective"));
        
        return schema;
    }
    
    /**
     * Output schema for planning.decompose tool.
     */
    public static ObjectNode planningDecomposeOutput() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = mapper.createObjectNode();
        
        ObjectNode subtasks = mapper.createObjectNode();
        subtasks.put("type", "array");
        subtasks.set("items", mapper.createObjectNode().put("type", "string"));
        properties.set("subtasks", subtasks);
        
        ObjectNode dependencies = mapper.createObjectNode();
        dependencies.put("type", "array");
        dependencies.set("items", mapper.createObjectNode().put("type", "string"));
        properties.set("dependencies", dependencies);
        
        schema.set("properties", properties);
        return schema;
    }
    
    /**
     * Input schema for planning.next_step tool.
     */
    public static ObjectNode planningNextStepInput() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = mapper.createObjectNode();
        
        ObjectNode currentState = mapper.createObjectNode();
        currentState.put("type", "string");
        currentState.put("description", "Description of the current reasoning state");
        properties.set("currentState", currentState);
        
        ObjectNode availableTools = mapper.createObjectNode();
        availableTools.put("type", "array");
        availableTools.set("items", mapper.createObjectNode().put("type", "string"));
        availableTools.put("description", "List of available tool names");
        properties.set("availableTools", availableTools);
        
        schema.set("properties", properties);
        schema.set("required", mapper.createArrayNode().add("currentState"));
        
        return schema;
    }
    
    /**
     * Output schema for planning.next_step tool.
     */
    public static ObjectNode planningNextStepOutput() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = mapper.createObjectNode();
        
        ObjectNode action = mapper.createObjectNode();
        action.put("type", "string");
        action.put("enum", "call_tool,skip,proceed");
        properties.set("action", action);
        
        ObjectNode toolName = mapper.createObjectNode();
        toolName.put("type", "string");
        properties.set("toolName", toolName);
        
        ObjectNode reasoning = mapper.createObjectNode();
        reasoning.put("type", "string");
        properties.set("reasoning", reasoning);
        
        schema.set("properties", properties);
        return schema;
    }
    
    // ============ Reasoning Tools ============
    
    /**
     * Input schema for reasoning.compare tool.
     */
    public static ObjectNode reasoningCompareInput() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = mapper.createObjectNode();
        
        ObjectNode items = mapper.createObjectNode();
        items.put("type", "array");
        items.set("items", mapper.createObjectNode().put("type", "string"));
        items.put("description", "Items to compare");
        properties.set("items", items);
        
        ObjectNode criteria = mapper.createObjectNode();
        criteria.put("type", "string");
        criteria.put("description", "Criteria for comparison");
        properties.set("criteria", criteria);
        
        schema.set("properties", properties);
        ArrayNode required = mapper.createArrayNode();
        required.add("items");
        required.add("criteria");
        schema.set("required", required);
        
        return schema;
    }
    
    /**
     * Output schema for reasoning.compare tool.
     */
    public static ObjectNode reasoningCompareOutput() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = mapper.createObjectNode();
        
        ObjectNode comparison = mapper.createObjectNode();
        comparison.put("type", "string");
        properties.set("comparison", comparison);
        
        ObjectNode winner = mapper.createObjectNode();
        winner.put("type", "string");
        properties.set("winner", winner);
        
        ObjectNode confidence = mapper.createObjectNode();
        confidence.put("type", "number");
        confidence.put("minimum", 0);
        confidence.put("maximum", 1);
        properties.set("confidence", confidence);
        
        schema.set("properties", properties);
        return schema;
    }
    
    /**
     * Input schema for reasoning.analyze tool.
     */
    public static ObjectNode reasoningAnalyzeInput() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = mapper.createObjectNode();
        
        ObjectNode data = mapper.createObjectNode();
        data.put("type", "string");
        data.put("description", "Data to analyze");
        properties.set("data", data);
        
        ObjectNode question = mapper.createObjectNode();
        question.put("type", "string");
        question.put("description", "Question guiding the analysis");
        properties.set("question", question);
        
        schema.set("properties", properties);
        ArrayNode required = mapper.createArrayNode();
        required.add("data");
        required.add("question");
        schema.set("required", required);
        
        return schema;
    }
    
    /**
     * Output schema for reasoning.analyze tool.
     */
    public static ObjectNode reasoningAnalyzeOutput() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = mapper.createObjectNode();
        
        ObjectNode analysis = mapper.createObjectNode();
        analysis.put("type", "string");
        properties.set("analysis", analysis);
        
        ObjectNode insights = mapper.createObjectNode();
        insights.put("type", "array");
        insights.set("items", mapper.createObjectNode().put("type", "string"));
        properties.set("insights", insights);
        
        ObjectNode confidence = mapper.createObjectNode();
        confidence.put("type", "number");
        confidence.put("minimum", 0);
        confidence.put("maximum", 1);
        properties.set("confidence", confidence);
        
        schema.set("properties", properties);
        return schema;
    }
    
    // ============ Memory Tools ============
    
    /**
     * Input schema for memory.store tool.
     */
    public static ObjectNode memoryStoreInput() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = mapper.createObjectNode();
        
        ObjectNode lane = mapper.createObjectNode();
        lane.put("type", "string");
        lane.put("enum", "facts,plans");
        lane.put("description", "Memory lane (facts or plans)");
        properties.set("lane", lane);
        
        ObjectNode key = mapper.createObjectNode();
        key.put("type", "string");
        key.put("description", "Key to store the value under");
        properties.set("key", key);
        
        ObjectNode value = mapper.createObjectNode();
        value.put("type", "string");
        value.put("description", "Value to store");
        properties.set("value", value);
        
        ObjectNode ttl = mapper.createObjectNode();
        ttl.put("type", "integer");
        ttl.put("description", "TTL in seconds (default: 1800)");
        ttl.put("default", 1800);
        properties.set("ttl", ttl);
        
        schema.set("properties", properties);
        ArrayNode required = mapper.createArrayNode();
        required.add("lane");
        required.add("key");
        required.add("value");
        schema.set("required", required);
        
        return schema;
    }
    
    /**
     * Output schema for memory.store tool.
     */
    public static ObjectNode memoryStoreOutput() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = mapper.createObjectNode();
        
        ObjectNode stored = mapper.createObjectNode();
        stored.put("type", "boolean");
        properties.set("stored", stored);
        
        ObjectNode key = mapper.createObjectNode();
        key.put("type", "string");
        properties.set("key", key);
        
        schema.set("properties", properties);
        return schema;
    }
    
    /**
     * Input schema for memory.recall tool.
     */
    public static ObjectNode memoryRecallInput() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = mapper.createObjectNode();
        
        ObjectNode lane = mapper.createObjectNode();
        lane.put("type", "string");
        lane.put("enum", "facts,plans");
        lane.put("description", "Memory lane (facts or plans)");
        properties.set("lane", lane);
        
        ObjectNode key = mapper.createObjectNode();
        key.put("type", "string");
        key.put("description", "Key to retrieve");
        properties.set("key", key);
        
        schema.set("properties", properties);
        ArrayNode required = mapper.createArrayNode();
        required.add("lane");
        required.add("key");
        schema.set("required", required);
        
        return schema;
    }
    
    /**
     * Output schema for memory.recall tool.
     */
    public static ObjectNode memoryRecallOutput() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = mapper.createObjectNode();
        
        ObjectNode found = mapper.createObjectNode();
        found.put("type", "boolean");
        properties.set("found", found);
        
        ObjectNode value = mapper.createObjectNode();
        value.put("type", "string");
        properties.set("value", value);
        
        schema.set("properties", properties);
        return schema;
    }
}
