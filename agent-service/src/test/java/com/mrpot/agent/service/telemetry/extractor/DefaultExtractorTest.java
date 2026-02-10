package com.mrpot.agent.service.telemetry.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefaultExtractorTest {

    private DefaultExtractor extractor;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        extractor = new DefaultExtractor();
        objectMapper = new ObjectMapper();
    }

    @Test
    void supports_always_returns_true() {
        assertTrue(extractor.supports("any.tool"));
        assertTrue(extractor.supports("unknown.operation"));
        assertTrue(extractor.supports(""));
        assertTrue(extractor.supports(null));
    }

    @Test
    void extractFromArgs_returns_type_for_object() throws Exception {
        JsonNode args = objectMapper.readTree("""
            {
                "key": "value",
                "number": 123,
                "nested": {"inner": true}
            }
            """);

        Map<String, Object> info = extractor.extractFromArgs("any.tool", args);

        assertEquals("object", info.get("argsType"));
        assertEquals(3, info.get("argsFieldCount"));
    }

    @Test
    void extractFromArgs_returns_type_for_array() throws Exception {
        JsonNode args = objectMapper.readTree("""
            ["item1", "item2", "item3"]
            """);

        Map<String, Object> info = extractor.extractFromArgs("any.tool", args);

        assertEquals("array", info.get("argsType"));
        assertEquals(3, info.get("argsElementCount"));
    }

    @Test
    void extractFromArgs_returns_type_for_string() throws Exception {
        JsonNode args = objectMapper.readTree("\"simple string\"");

        Map<String, Object> info = extractor.extractFromArgs("any.tool", args);

        assertEquals("string", info.get("argsType"));
    }

    @Test
    void extractFromArgs_returns_type_for_number() throws Exception {
        JsonNode args = objectMapper.readTree("42");

        Map<String, Object> info = extractor.extractFromArgs("any.tool", args);

        assertEquals("number", info.get("argsType"));
    }

    @Test
    void extractFromArgs_returns_type_for_boolean() throws Exception {
        JsonNode args = objectMapper.readTree("true");

        Map<String, Object> info = extractor.extractFromArgs("any.tool", args);

        assertEquals("boolean", info.get("argsType"));
    }

    @Test
    void extractFromArgs_handles_null() {
        Map<String, Object> info = extractor.extractFromArgs("any.tool", null);

        assertEquals("null", info.get("argsType"));
    }

    @Test
    void extractFromResult_returns_type_for_object() throws Exception {
        JsonNode result = objectMapper.readTree("""
            {
                "status": "success",
                "data": []
            }
            """);

        Map<String, Object> info = extractor.extractFromResult("any.tool", result);

        assertEquals("object", info.get("resultType"));
        assertEquals(2, info.get("resultFieldCount"));
    }

    @Test
    void extractFromResult_returns_type_for_array() throws Exception {
        JsonNode result = objectMapper.readTree("""
            [1, 2, 3, 4, 5]
            """);

        Map<String, Object> info = extractor.extractFromResult("any.tool", result);

        assertEquals("array", info.get("resultType"));
        assertEquals(5, info.get("resultElementCount"));
    }

    @Test
    void extractFromResult_handles_null() {
        Map<String, Object> info = extractor.extractFromResult("any.tool", null);

        assertEquals("null", info.get("resultType"));
    }

    @Test
    void extractFromResult_handles_empty_array() throws Exception {
        JsonNode result = objectMapper.readTree("[]");

        Map<String, Object> info = extractor.extractFromResult("any.tool", result);

        assertEquals("array", info.get("resultType"));
        assertEquals(0, info.get("resultElementCount"));
    }

    @Test
    void extractFromResult_handles_empty_object() throws Exception {
        JsonNode result = objectMapper.readTree("{}");

        Map<String, Object> info = extractor.extractFromResult("any.tool", result);

        assertEquals("object", info.get("resultType"));
        assertEquals(0, info.get("resultFieldCount"));
    }
}
