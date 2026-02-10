package com.mrpot.agent.service.telemetry.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RedisExtractorTest {

    private RedisExtractor extractor;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        extractor = new RedisExtractor();
        objectMapper = new ObjectMapper();
    }

    @Test
    void supports_returns_true_for_redis_tools() {
        assertTrue(extractor.supports("redis.get"));
        assertTrue(extractor.supports("redis.set"));
        assertTrue(extractor.supports("redis.mget"));
        assertTrue(extractor.supports("REDIS.GET"));
    }

    @Test
    void supports_returns_true_for_cache_tools() {
        assertTrue(extractor.supports("cache.get"));
        assertTrue(extractor.supports("cache.set"));
        assertTrue(extractor.supports("CACHE.INVALIDATE"));
    }

    @Test
    void supports_returns_false_for_other_tools() {
        assertFalse(extractor.supports("kb.search"));
        assertFalse(extractor.supports("file.extract"));
        assertFalse(extractor.supports("other.tool"));
    }

    @Test
    void supports_handles_null() {
        assertFalse(extractor.supports(null));
    }

    @Test
    void extractFromArgs_extracts_key_info() throws Exception {
        JsonNode args = objectMapper.readTree("""
            {
                "key": "user:12345:profile"
            }
            """);

        Map<String, Object> info = extractor.extractFromArgs("redis.get", args);

        assertEquals(18, info.get("keyLength"));
        assertEquals("user", info.get("keyPrefix"));
        assertEquals("get", info.get("operation"));
    }

    @Test
    void extractFromArgs_extracts_multi_key_info() throws Exception {
        JsonNode args = objectMapper.readTree("""
            {
                "keys": ["key1", "key2", "key3"]
            }
            """);

        Map<String, Object> info = extractor.extractFromArgs("redis.mget", args);

        assertEquals(3, info.get("keyCount"));
    }

    @Test
    void extractFromArgs_extracts_ttl() throws Exception {
        JsonNode args = objectMapper.readTree("""
            {
                "key": "session:abc",
                "value": "data",
                "ttl": 3600
            }
            """);

        Map<String, Object> info = extractor.extractFromArgs("redis.set", args);

        assertEquals(3600L, info.get("ttlSeconds"));
    }

    @Test
    void extractFromArgs_extracts_expire_seconds() throws Exception {
        JsonNode args = objectMapper.readTree("""
            {
                "key": "temp:key",
                "expireSeconds": 7200
            }
            """);

        Map<String, Object> info = extractor.extractFromArgs("redis.set", args);

        assertEquals(7200L, info.get("ttlSeconds"));
    }

    @Test
    void extractFromArgs_extracts_value_size_for_string() throws Exception {
        JsonNode args = objectMapper.readTree("""
            {
                "key": "data:key",
                "value": "this is a test value"
            }
            """);

        Map<String, Object> info = extractor.extractFromArgs("redis.set", args);

        assertEquals(20, info.get("valueSize"));
    }

    @Test
    void extractFromArgs_extracts_value_size_for_object() throws Exception {
        JsonNode args = objectMapper.readTree("""
            {
                "key": "user:profile",
                "value": {"name": "John", "age": 30}
            }
            """);

        Map<String, Object> info = extractor.extractFromArgs("redis.set", args);

        // Implementation stores size of JSON string, not type
        assertNotNull(info.get("valueSize"));
    }

    @Test
    void extractFromArgs_handles_null() {
        Map<String, Object> info = extractor.extractFromArgs("redis.get", null);
        
        // When args is null, implementation returns empty map
        assertTrue(info.isEmpty());
    }

    @Test
    void extractFromArgs_extracts_key_prefix_correctly() throws Exception {
        // Key with colon prefix
        JsonNode args1 = objectMapper.readTree("""
            {"key": "session:12345"}
            """);
        assertEquals("session", extractor.extractFromArgs("redis.get", args1).get("keyPrefix"));

        // Key without colon - returns full key up to 32 chars
        JsonNode args2 = objectMapper.readTree("""
            {"key": "simplekey"}
            """);
        assertEquals("simplekey", extractor.extractFromArgs("redis.get", args2).get("keyPrefix"));

        // Key with multiple colons - returns first segment
        JsonNode args3 = objectMapper.readTree("""
            {"key": "user:org:123:data"}
            """);
        assertEquals("user", extractor.extractFromArgs("redis.get", args3).get("keyPrefix"));
    }

    @Test
    void extractFromResult_extracts_hit_info() throws Exception {
        JsonNode result = objectMapper.readTree("""
            {
                "found": true,
                "value": "cached data",
                "ttlRemaining": 1800
            }
            """);

        Map<String, Object> info = extractor.extractFromResult("redis.get", result);

        assertTrue((Boolean) info.get("cacheHit"));
        assertEquals(1800L, info.get("ttlRemaining"));
        assertNotNull(info.get("valueSize"));
    }

    @Test
    void extractFromResult_extracts_miss_info() throws Exception {
        JsonNode result = objectMapper.readTree("""
            {
                "value": null
            }
            """);

        Map<String, Object> info = extractor.extractFromResult("redis.get", result);

        assertFalse((Boolean) info.get("cacheHit"));
    }

    @Test
    void extractFromResult_handles_success_status() throws Exception {
        JsonNode result = objectMapper.readTree("""
            {
                "success": true
            }
            """);

        Map<String, Object> info = extractor.extractFromResult("redis.set", result);

        assertTrue((Boolean) info.get("operationSuccess"));
    }

    @Test
    void extractFromResult_handles_null() {
        Map<String, Object> info = extractor.extractFromResult("redis.get", null);
        assertTrue(info.isEmpty());
    }

    @Test
    void extractOperation_parses_correctly() throws Exception {
        assertEquals("get", extractor.extractFromArgs("redis.get", objectMapper.readTree("{}")).get("operation"));
        assertEquals("set", extractor.extractFromArgs("redis.set", objectMapper.readTree("{}")).get("operation"));
        assertEquals("mget", extractor.extractFromArgs("redis.mget", objectMapper.readTree("{}")).get("operation"));
        assertEquals("delete", extractor.extractFromArgs("cache.delete", objectMapper.readTree("{}")).get("operation"));
    }
}
