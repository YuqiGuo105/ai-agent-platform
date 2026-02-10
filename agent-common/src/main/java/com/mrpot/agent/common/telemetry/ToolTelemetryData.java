package com.mrpot.agent.common.telemetry;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Detailed data payload for tool call telemetry.
 * Contains both digests (for deduplication/comparison) and previews (for debugging).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolTelemetryData(
    // Tool identification
    String toolName,           // e.g., "kb.search", "file.extract_url", "redis.get"
    Integer attempt,           // retry attempt number (1-based)
    
    // Arguments info (captured at tool.start)
    String argsDigest,         // SHA-256 of normalized JSON args (for deduplication)
    String argsPreview,        // first N chars of args (sanitized, max 500 chars)
    Integer argsSize,          // original args size in bytes
    
    // Result info (captured at tool.end)
    String resultDigest,       // SHA-256 of result JSON
    String resultPreview,      // first N chars of result (sanitized, max 500 chars)
    Integer resultSize,        // result size in bytes
    
    // Timing and cache info
    Long durationMs,           // execution duration in milliseconds
    Boolean cacheHit,          // whether result came from cache
    Long ttlHintSeconds,       // TTL hint for caching
    String freshness,          // FRESH / STALE / MISS
    
    // Error info (captured at tool.error)
    String errorCode,          // error code (NOT_FOUND, TIMEOUT, INTERNAL, etc.)
    String errorMessage,       // error message (sanitized)
    Boolean retryable,         // whether error is retryable
    
    // Extracted key info (tool-specific)
    Map<String, Object> keyInfo  // extracted info like kbHits, queryTerms, etc.
) {
    /**
     * Builder for creating ToolTelemetryData incrementally.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String toolName;
        private Integer attempt;
        private String argsDigest;
        private String argsPreview;
        private Integer argsSize;
        private String resultDigest;
        private String resultPreview;
        private Integer resultSize;
        private Long durationMs;
        private Boolean cacheHit;
        private Long ttlHintSeconds;
        private String freshness;
        private String errorCode;
        private String errorMessage;
        private Boolean retryable;
        private Map<String, Object> keyInfo;
        
        public Builder toolName(String toolName) { this.toolName = toolName; return this; }
        public Builder attempt(Integer attempt) { this.attempt = attempt; return this; }
        public Builder argsDigest(String argsDigest) { this.argsDigest = argsDigest; return this; }
        public Builder argsPreview(String argsPreview) { this.argsPreview = argsPreview; return this; }
        public Builder argsSize(Integer argsSize) { this.argsSize = argsSize; return this; }
        public Builder resultDigest(String resultDigest) { this.resultDigest = resultDigest; return this; }
        public Builder resultPreview(String resultPreview) { this.resultPreview = resultPreview; return this; }
        public Builder resultSize(Integer resultSize) { this.resultSize = resultSize; return this; }
        public Builder durationMs(Long durationMs) { this.durationMs = durationMs; return this; }
        public Builder cacheHit(Boolean cacheHit) { this.cacheHit = cacheHit; return this; }
        public Builder ttlHintSeconds(Long ttlHintSeconds) { this.ttlHintSeconds = ttlHintSeconds; return this; }
        public Builder freshness(String freshness) { this.freshness = freshness; return this; }
        public Builder errorCode(String errorCode) { this.errorCode = errorCode; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        public Builder retryable(Boolean retryable) { this.retryable = retryable; return this; }
        public Builder keyInfo(Map<String, Object> keyInfo) { this.keyInfo = keyInfo; return this; }
        
        public ToolTelemetryData build() {
            return new ToolTelemetryData(
                toolName, attempt, argsDigest, argsPreview, argsSize,
                resultDigest, resultPreview, resultSize,
                durationMs, cacheHit, ttlHintSeconds, freshness,
                errorCode, errorMessage, retryable, keyInfo
            );
        }
    }
}
