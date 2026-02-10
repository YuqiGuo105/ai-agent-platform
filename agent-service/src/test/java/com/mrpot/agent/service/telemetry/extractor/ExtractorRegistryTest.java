package com.mrpot.agent.service.telemetry.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExtractorRegistryTest {

    private ExtractorRegistry registry;
    private KbSearchExtractor kbSearchExtractor;
    private FileExtractUrlExtractor fileExtractExtractor;
    private RedisExtractor redisExtractor;
    private DefaultExtractor defaultExtractor;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        kbSearchExtractor = new KbSearchExtractor();
        fileExtractExtractor = new FileExtractUrlExtractor();
        redisExtractor = new RedisExtractor();
        defaultExtractor = new DefaultExtractor();
        objectMapper = new ObjectMapper();
        
        List<ToolKeyInfoExtractor> extractors = List.of(
            kbSearchExtractor,
            fileExtractExtractor,
            redisExtractor,
            defaultExtractor
        );
        
        registry = new ExtractorRegistry(extractors, defaultExtractor);
    }

    @Test
    void getExtractor_returns_kb_search_extractor() {
        ToolKeyInfoExtractor extractor = registry.getExtractor("kb.search");
        assertInstanceOf(KbSearchExtractor.class, extractor);
    }

    @Test
    void getExtractor_returns_file_extract_extractor() {
        ToolKeyInfoExtractor extractor = registry.getExtractor("file.extract_url");
        assertInstanceOf(FileExtractUrlExtractor.class, extractor);
    }

    @Test
    void getExtractor_returns_redis_extractor() {
        ToolKeyInfoExtractor extractor = registry.getExtractor("redis.get");
        assertInstanceOf(RedisExtractor.class, extractor);
        
        extractor = registry.getExtractor("cache.set");
        assertInstanceOf(RedisExtractor.class, extractor);
    }

    @Test
    void getExtractor_returns_default_for_unknown_tool() {
        ToolKeyInfoExtractor extractor = registry.getExtractor("unknown.tool");
        assertInstanceOf(DefaultExtractor.class, extractor);
    }

    @Test
    void getExtractor_returns_default_for_null_tool() {
        ToolKeyInfoExtractor extractor = registry.getExtractor(null);
        assertInstanceOf(DefaultExtractor.class, extractor);
    }

    @Test
    void extractFromArgs_delegates_to_correct_extractor() throws Exception {
        JsonNode kbArgs = objectMapper.readTree("""
            {"query": "test query", "limit": 5}
            """);
        
        Map<String, Object> info = registry.extractFromArgs("kb.search", kbArgs);
        
        // KB extractor should extract query-specific info
        assertNotNull(info.get("queryLength"));
        assertNotNull(info.get("queryTermCount"));
    }

    @Test
    void extractFromArgs_uses_default_for_unknown_tool() throws Exception {
        JsonNode args = objectMapper.readTree("""
            {"field1": "value1", "field2": "value2"}
            """);
        
        Map<String, Object> info = registry.extractFromArgs("unknown.tool", args);
        
        // Default extractor should return type info
        assertEquals("object", info.get("argsType"));
        assertEquals(2, info.get("argsFieldCount"));
    }

    @Test
    void extractFromResult_delegates_to_correct_extractor() throws Exception {
        JsonNode kbResult = objectMapper.readTree("""
            {"hits": [{"id": "doc1"}, {"id": "doc2"}], "total": 10}
            """);
        
        Map<String, Object> info = registry.extractFromResult("kb.search", kbResult);
        
        // KB extractor should extract hit-specific info
        assertEquals(2, info.get("hitCount"));
        assertEquals(10, info.get("totalMatches"));
    }

    @Test
    void extractFromResult_uses_default_for_unknown_tool() throws Exception {
        JsonNode result = objectMapper.readTree("""
            [1, 2, 3]
            """);
        
        Map<String, Object> info = registry.extractFromResult("unknown.tool", result);
        
        // Default extractor should return type info
        assertEquals("array", info.get("resultType"));
        assertEquals(3, info.get("resultElementCount"));
    }

    @Test
    void registry_handles_case_insensitive_tool_names() throws Exception {
        JsonNode args = objectMapper.readTree("""
            {"query": "test", "limit": 5}
            """);
        
        // These should all use KB extractor
        Map<String, Object> info1 = registry.extractFromArgs("kb.search", args);
        Map<String, Object> info2 = registry.extractFromArgs("KB.SEARCH", args);
        Map<String, Object> info3 = registry.extractFromArgs("Kb.Search", args);
        
        // All should have KB-specific info
        assertNotNull(info1.get("queryLength"));
        assertNotNull(info2.get("queryLength"));
        assertNotNull(info3.get("queryLength"));
    }

    @Test
    void registry_excludes_default_from_search() {
        // Create registry where default extractor is also in the list
        List<ToolKeyInfoExtractor> extractorsWithDefault = List.of(
            defaultExtractor,
            kbSearchExtractor
        );
        ExtractorRegistry registryWithDefault = new ExtractorRegistry(extractorsWithDefault, defaultExtractor);
        
        // Should still return specific extractor, not default
        ToolKeyInfoExtractor extractor = registryWithDefault.getExtractor("kb.search");
        assertInstanceOf(KbSearchExtractor.class, extractor);
    }
}
