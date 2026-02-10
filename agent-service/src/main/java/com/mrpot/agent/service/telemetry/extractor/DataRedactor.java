package com.mrpot.agent.service.telemetry.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Global data redactor for removing sensitive information from telemetry data.
 * Applied before publishing any telemetry event.
 */
@Component
public class DataRedactor {
    
    private static final Logger log = LoggerFactory.getLogger(DataRedactor.class);
    
    // Sensitive field names (case-insensitive matching)
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
        "authorization", "auth", "token", "bearer",
        "cookie", "cookies", "session",
        "apikey", "api_key", "api-key",
        "password", "passwd", "pwd", "secret",
        "credential", "credentials",
        "private_key", "privatekey",
        "access_token", "refresh_token",
        "client_secret", "clientsecret"
    );
    
    // Pattern for sensitive values (tokens, keys, etc.)
    private static final Pattern BEARER_PATTERN = Pattern.compile(
        "Bearer\\s+[A-Za-z0-9\\-_\\.]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern KEY_PATTERN = Pattern.compile(
        "(?:sk|pk|api|key|token)[_-]?[A-Za-z0-9]{20,}", Pattern.CASE_INSENSITIVE);
    
    private static final String REDACTED = "[REDACTED]";
    private static final int MAX_PREVIEW_LENGTH = 500;
    private static final int MAX_DEPTH = 10;
    
    private final ObjectMapper objectMapper;
    
    public DataRedactor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Redact sensitive data from JSON node.
     * Returns a new node with sensitive fields replaced.
     */
    public JsonNode redact(JsonNode node) {
        if (node == null) {
            return null;
        }
        return redactRecursive(node.deepCopy(), 0);
    }
    
    /**
     * Create a preview string from JSON, redacted and truncated.
     */
    public String createPreview(JsonNode node, int maxLength) {
        if (node == null) {
            return null;
        }
        
        JsonNode redacted = redact(node);
        String json = redacted.toString();
        
        if (json.length() <= maxLength) {
            return json;
        }
        return json.substring(0, maxLength - 3) + "...";
    }
    
    /**
     * Create a preview string with default max length.
     */
    public String createPreview(JsonNode node) {
        return createPreview(node, MAX_PREVIEW_LENGTH);
    }
    
    /**
     * Calculate SHA-256 digest of JSON content (after normalization).
     */
    public String calculateDigest(JsonNode node) {
        if (node == null) {
            return null;
        }
        
        try {
            // Normalize by converting to sorted JSON string
            String normalized = objectMapper.writeValueAsString(node);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.warn("Failed to calculate digest: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Get the size of JSON content in bytes.
     */
    public int getSize(JsonNode node) {
        if (node == null) {
            return 0;
        }
        try {
            return objectMapper.writeValueAsBytes(node).length;
        } catch (Exception e) {
            return 0;
        }
    }
    
    private JsonNode redactRecursive(JsonNode node, int depth) {
        if (depth > MAX_DEPTH || node == null) {
            return node;
        }
        
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            obj.fieldNames().forEachRemaining(fieldName -> {
                if (isSensitiveField(fieldName)) {
                    obj.put(fieldName, REDACTED);
                } else {
                    JsonNode child = obj.get(fieldName);
                    if (child != null) {
                        if (child.isTextual()) {
                            obj.put(fieldName, redactValue(child.asText()));
                        } else {
                            obj.set(fieldName, redactRecursive(child, depth + 1));
                        }
                    }
                }
            });
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                JsonNode element = node.get(i);
                if (element.isTextual()) {
                    ((com.fasterxml.jackson.databind.node.ArrayNode) node)
                        .set(i, objectMapper.valueToTree(redactValue(element.asText())));
                } else {
                    redactRecursive(element, depth + 1);
                }
            }
        }
        
        return node;
    }
    
    private boolean isSensitiveField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String lower = fieldName.toLowerCase();
        return SENSITIVE_FIELDS.stream().anyMatch(lower::contains);
    }
    
    private String redactValue(String value) {
        if (value == null) {
            return null;
        }
        
        // Redact bearer tokens
        String result = BEARER_PATTERN.matcher(value).replaceAll(REDACTED);
        
        // Redact API keys / tokens
        result = KEY_PATTERN.matcher(result).replaceAll(REDACTED);
        
        return result;
    }
}
