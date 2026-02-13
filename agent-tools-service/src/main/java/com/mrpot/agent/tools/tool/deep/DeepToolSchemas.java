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
    
    // ============ Verification Tools (Sprint 4) ============
    
    /**
     * Input schema for verify.consistency tool.
     */
    public static ObjectNode verifyConsistencyInput() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = mapper.createObjectNode();
        
        // reasoningArtifacts array
        ObjectNode reasoningArtifacts = mapper.createObjectNode();
        reasoningArtifacts.put("type", "array");
        reasoningArtifacts.put("description", "List of reasoning steps to check for consistency");
        
        ObjectNode artifactItem = mapper.createObjectNode();
        artifactItem.put("type", "object");
        ObjectNode artifactProps = mapper.createObjectNode();
        artifactProps.set("hypothesis", mapper.createObjectNode().put("type", "string"));
        ObjectNode evidenceRefs = mapper.createObjectNode();
        evidenceRefs.put("type", "array");
        evidenceRefs.set("items", mapper.createObjectNode().put("type", "string"));
        artifactProps.set("evidenceRefs", evidenceRefs);
        artifactProps.set("confidence", mapper.createObjectNode().put("type", "number"));
        artifactItem.set("properties", artifactProps);
        
        reasoningArtifacts.set("items", artifactItem);
        properties.set("reasoningArtifacts", reasoningArtifacts);
        
        schema.set("properties", properties);
        schema.set("required", mapper.createArrayNode().add("reasoningArtifacts"));
        
        return schema;
    }
    
    /**
     * Output schema for verify.consistency tool.
     */
    public static ObjectNode verifyConsistencyOutput() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = mapper.createObjectNode();
        
        ObjectNode consistencyScore = mapper.createObjectNode();
        consistencyScore.put("type", "number");
        consistencyScore.put("description", "Consistency score from 0.0 to 1.0");
        properties.set("consistencyScore", consistencyScore);
        
        ObjectNode contradictions = mapper.createObjectNode();
        contradictions.put("type", "array");
        ObjectNode contradictionItem = mapper.createObjectNode();
        contradictionItem.put("type", "object");
        ObjectNode contradictionProps = mapper.createObjectNode();
        contradictionProps.set("stepA", mapper.createObjectNode().put("type", "integer"));
        contradictionProps.set("stepB", mapper.createObjectNode().put("type", "integer"));
        contradictionProps.set("description", mapper.createObjectNode().put("type", "string"));
        contradictionItem.set("properties", contradictionProps);
        contradictions.set("items", contradictionItem);
        properties.set("contradictions", contradictions);
        
        ObjectNode flags = mapper.createObjectNode();
        flags.put("type", "array");
        flags.set("items", mapper.createObjectNode().put("type", "string"));
        properties.set("flags", flags);
        
        schema.set("properties", properties);
        return schema;
    }
    
    /**
     * Input schema for verify.fact_check tool.
     */
    public static ObjectNode verifyFactCheckInput() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = mapper.createObjectNode();
        
        ObjectNode claims = mapper.createObjectNode();
        claims.put("type", "array");
        claims.put("description", "List of claims to verify");
        claims.set("items", mapper.createObjectNode().put("type", "string"));
        properties.set("claims", claims);
        
        ObjectNode evidenceSources = mapper.createObjectNode();
        evidenceSources.put("type", "array");
        evidenceSources.put("description", "Available evidence sources");
        evidenceSources.set("items", mapper.createObjectNode().put("type", "string"));
        properties.set("evidenceSources", evidenceSources);
        
        schema.set("properties", properties);
        schema.set("required", mapper.createArrayNode().add("claims"));
        
        return schema;
    }
    
    /**
     * Output schema for verify.fact_check tool.
     */
    public static ObjectNode verifyFactCheckOutput() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = mapper.createObjectNode();
        
        ObjectNode factualityFlags = mapper.createObjectNode();
        factualityFlags.put("type", "array");
        ObjectNode flagItem = mapper.createObjectNode();
        flagItem.put("type", "object");
        ObjectNode flagProps = mapper.createObjectNode();
        flagProps.set("claim", mapper.createObjectNode().put("type", "string"));
        ObjectNode verdict = mapper.createObjectNode();
        verdict.put("type", "string");
        ArrayNode verdictEnum = mapper.createArrayNode();
        verdictEnum.add("supported");
        verdictEnum.add("unsupported");
        verdictEnum.add("unverifiable");
        verdict.set("enum", verdictEnum);
        flagProps.set("verdict", verdict);
        flagProps.set("confidence", mapper.createObjectNode().put("type", "number"));
        flagItem.set("properties", flagProps);
        factualityFlags.set("items", flagItem);
        properties.set("factualityFlags", factualityFlags);
        
        ObjectNode unresolvedClaims = mapper.createObjectNode();
        unresolvedClaims.put("type", "array");
        unresolvedClaims.set("items", mapper.createObjectNode().put("type", "string"));
        properties.set("unresolvedClaims", unresolvedClaims);
        
        schema.set("properties", properties);
        return schema;
    }
}
