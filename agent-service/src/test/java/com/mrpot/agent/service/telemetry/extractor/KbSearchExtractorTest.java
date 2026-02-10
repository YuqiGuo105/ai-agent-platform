package com.mrpot.agent.service.telemetry.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KbSearchExtractorTest {

    private KbSearchExtractor extractor;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        extractor = new KbSearchExtractor();
        objectMapper = new ObjectMapper();
    }

    @Test
    void supports_returns_true_for_kb_search() {
        assertTrue(extractor.supports("kb.search"));
        assertTrue(extractor.supports("KB.SEARCH"));
        assertTrue(extractor.supports("Kb.Search"));
    }

    @Test
    void supports_returns_false_for_other_tools() {
        assertFalse(extractor.supports("file.extract"));
        assertFalse(extractor.supports("redis.get"));
        assertFalse(extractor.supports("other.tool"));
    }

    @Test
    void extractFromArgs_extracts_query_info() throws Exception {
        JsonNode args = objectMapper.readTree("""
            {
                "query": "What is machine learning?",
                "scopeMode": "GENERAL",
                "limit": 10
            }
            """);

        Map<String, Object> info = extractor.extractFromArgs("kb.search", args);

        assertEquals(25, info.get("queryLength"));
        assertEquals(4, info.get("queryTermCount"));
        assertNotNull(info.get("queryPreview"));
        assertEquals("GENERAL", info.get("scopeMode"));
        assertEquals(10, info.get("requestedLimit"));
    }

    @Test
    void extractFromArgs_handles_filters() throws Exception {
        JsonNode args = objectMapper.readTree("""
            {
                "query": "test query",
                "filters": {"category": "tech"}
            }
            """);

        Map<String, Object> info = extractor.extractFromArgs("kb.search", args);

        assertTrue((Boolean) info.get("hasFilters"));
    }

    @Test
    void extractFromArgs_handles_null() {
        Map<String, Object> info = extractor.extractFromArgs("kb.search", null);
        assertTrue(info.isEmpty());
    }

    @Test
    void extractFromArgs_truncates_long_query() throws Exception {
        String longQuery = "word ".repeat(50);
        JsonNode args = objectMapper.readTree("""
            {"query": "%s"}
            """.formatted(longQuery.trim()));

        Map<String, Object> info = extractor.extractFromArgs("kb.search", args);

        String preview = (String) info.get("queryPreview");
        // Truncated to 100 chars + "..." = 103 chars max
        assertTrue(preview.length() <= 103);
    }

    @Test
    void extractFromResult_extracts_hit_count() throws Exception {
        JsonNode result = objectMapper.readTree("""
            {
                "hits": [
                    {"id": "doc1", "score": 0.95},
                    {"id": "doc2", "score": 0.85},
                    {"id": "doc3", "score": 0.75}
                ],
                "total": 100
            }
            """);

        Map<String, Object> info = extractor.extractFromResult("kb.search", result);

        assertEquals(3, info.get("hitCount"));
        assertEquals(100, info.get("totalMatches"));
        assertEquals("doc1,doc2,doc3", info.get("docIds"));
    }

    @Test
    void extractFromResult_limits_doc_ids_to_10() throws Exception {
        StringBuilder hitsBuilder = new StringBuilder("[");
        for (int i = 0; i < 15; i++) {
            if (i > 0) hitsBuilder.append(",");
            hitsBuilder.append("{\"id\": \"doc").append(i).append("\", \"score\": 0.9}");
        }
        hitsBuilder.append("]");

        JsonNode result = objectMapper.readTree("""
            {"hits": %s}
            """.formatted(hitsBuilder.toString()));

        Map<String, Object> info = extractor.extractFromResult("kb.search", result);

        String docIds = (String) info.get("docIds");
        int commaCount = docIds.split(",").length;
        assertEquals(10, commaCount); // Only first 10 docs
    }

    @Test
    void extractFromResult_extracts_top_score() throws Exception {
        JsonNode result = objectMapper.readTree("""
            {
                "hits": [{"id": "doc1", "score": 0.95}],
                "latencyMs": 150
            }
            """);

        Map<String, Object> info = extractor.extractFromResult("kb.search", result);

        assertEquals(0.95, info.get("topScore"));
        assertEquals(150L, info.get("searchLatencyMs"));
    }

    @Test
    void extractFromResult_handles_null() {
        Map<String, Object> info = extractor.extractFromResult("kb.search", null);
        assertTrue(info.isEmpty());
    }

    @Test
    void extractFromResult_handles_empty_hits() throws Exception {
        JsonNode result = objectMapper.readTree("""
            {"hits": []}
            """);

        Map<String, Object> info = extractor.extractFromResult("kb.search", result);

        assertEquals(0, info.get("hitCount"));
        assertNull(info.get("docIds"));
    }
}
