package com.mrpot.agent.service.telemetry.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DataRedactorTest {

    private DataRedactor redactor;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        redactor = new DataRedactor(objectMapper);
    }

    @Test
    void redact_removes_authorization_header() throws Exception {
        JsonNode input = objectMapper.readTree("""
            {
                "headers": {
                    "Authorization": "Bearer sk-1234567890abcdef",
                    "Content-Type": "application/json"
                }
            }
            """);

        JsonNode result = redactor.redact(input);

        assertEquals("[REDACTED]", result.get("headers").get("Authorization").asText());
        assertEquals("application/json", result.get("headers").get("Content-Type").asText());
    }

    @Test
    void redact_removes_password_field() throws Exception {
        JsonNode input = objectMapper.readTree("""
            {
                "username": "john",
                "password": "secret123",
                "email": "john@example.com"
            }
            """);

        JsonNode result = redactor.redact(input);

        assertEquals("john", result.get("username").asText());
        assertEquals("[REDACTED]", result.get("password").asText());
        assertEquals("john@example.com", result.get("email").asText());
    }

    @Test
    void redact_removes_api_key_variations() throws Exception {
        JsonNode input = objectMapper.readTree("""
            {
                "apikey": "key123",
                "api_key": "key456",
                "api-key": "key789"
            }
            """);

        JsonNode result = redactor.redact(input);

        assertEquals("[REDACTED]", result.get("apikey").asText());
        assertEquals("[REDACTED]", result.get("api_key").asText());
        assertEquals("[REDACTED]", result.get("api-key").asText());
    }

    @Test
    void redact_removes_token_fields() throws Exception {
        JsonNode input = objectMapper.readTree("""
            {
                "access_token": "abc123",
                "refresh_token": "def456",
                "token": "ghi789"
            }
            """);

        JsonNode result = redactor.redact(input);

        assertEquals("[REDACTED]", result.get("access_token").asText());
        assertEquals("[REDACTED]", result.get("refresh_token").asText());
        assertEquals("[REDACTED]", result.get("token").asText());
    }

    @Test
    void redact_removes_bearer_token_in_value() throws Exception {
        JsonNode input = objectMapper.readTree("""
            {
                "auth": "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
            }
            """);

        JsonNode result = redactor.redact(input);

        // Since "auth" is a sensitive field name, it should be redacted
        assertEquals("[REDACTED]", result.get("auth").asText());
    }

    @Test
    void redact_handles_nested_objects() throws Exception {
        // Note: 'credentials' is a sensitive field name, so it gets redacted entirely
        // Testing nested sensitive fields with non-sensitive parent
        JsonNode input = objectMapper.readTree("""
            {
                "user": {
                    "name": "Alice",
                    "profile": {
                        "password": "secret",
                        "apiKey": "key123"
                    }
                }
            }
            """);

        JsonNode result = redactor.redact(input);

        assertEquals("Alice", result.get("user").get("name").asText());
        assertEquals("[REDACTED]", result.get("user").get("profile").get("password").asText());
        assertEquals("[REDACTED]", result.get("user").get("profile").get("apiKey").asText());
    }

    @Test
    void redact_handles_arrays() throws Exception {
        JsonNode input = objectMapper.readTree("""
            {
                "users": [
                    {"name": "Alice", "password": "pass1"},
                    {"name": "Bob", "password": "pass2"}
                ]
            }
            """);

        JsonNode result = redactor.redact(input);

        assertEquals("Alice", result.get("users").get(0).get("name").asText());
        assertEquals("[REDACTED]", result.get("users").get(0).get("password").asText());
        assertEquals("Bob", result.get("users").get(1).get("name").asText());
        assertEquals("[REDACTED]", result.get("users").get(1).get("password").asText());
    }

    @Test
    void redact_handles_null_input() {
        JsonNode result = redactor.redact(null);
        assertNull(result);
    }

    @Test
    void createPreview_truncates_long_content() throws Exception {
        JsonNode input = objectMapper.readTree("""
            {
                "data": "a very long string that should be truncated when creating a preview"
            }
            """);

        String preview = redactor.createPreview(input, 30);

        assertEquals(30, preview.length());
        assertTrue(preview.endsWith("..."));
    }

    @Test
    void createPreview_keeps_short_content() throws Exception {
        JsonNode input = objectMapper.readTree("""
            {"name": "test"}
            """);

        String preview = redactor.createPreview(input, 500);

        assertTrue(preview.contains("test"));
        assertFalse(preview.endsWith("..."));
    }

    @Test
    void calculateDigest_generates_consistent_hash() throws Exception {
        JsonNode input = objectMapper.readTree("""
            {"query": "test", "limit": 10}
            """);

        String digest1 = redactor.calculateDigest(input);
        String digest2 = redactor.calculateDigest(input);

        assertEquals(digest1, digest2);
        assertEquals(64, digest1.length()); // SHA-256 hex is 64 chars
    }

    @Test
    void calculateDigest_different_for_different_input() throws Exception {
        JsonNode input1 = objectMapper.readTree("""
            {"query": "test1"}
            """);
        JsonNode input2 = objectMapper.readTree("""
            {"query": "test2"}
            """);

        String digest1 = redactor.calculateDigest(input1);
        String digest2 = redactor.calculateDigest(input2);

        assertNotEquals(digest1, digest2);
    }

    @Test
    void calculateDigest_handles_null() {
        String digest = redactor.calculateDigest(null);
        assertNull(digest);
    }
}
