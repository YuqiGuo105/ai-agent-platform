package com.mrpot.agent.service.pipeline;

import com.mrpot.agent.common.api.RagAnswerRequest;
import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.policy.ExecutionPolicy;
import com.mrpot.agent.common.tool.FileItem;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Context object passed through pipeline stages.
 * Contains request information, execution configuration, and shared working memory.
 */
public class PipelineContext {
    
    // Immutable fields set at construction
    private final String runId;
    private final String traceId;
    private final String sessionId;
    private final String userId;
    private final RagAnswerRequest request;
    private final ScopeMode scopeMode;
    private final ExecutionPolicy policy;
    private final String executionMode;
    private final AtomicLong sseSeq;
    private final long startTimeMs;
    
    // Working memory for cross-stage data sharing
    private final Map<String, Object> workingMemory;
    
    // Standard keys for working memory
    public static final String KEY_EXTRACTED_FILES = "extractedFiles";
    public static final String KEY_RAG_CONTEXT = "ragContext";
    public static final String KEY_FINAL_ANSWER = "finalAnswer";
    
    /**
     * Create a new pipeline context.
     *
     * @param runId         unique run ID
     * @param traceId       distributed tracing ID
     * @param sessionId     session ID
     * @param userId        user ID
     * @param request       the original request
     * @param scopeMode     determined scope mode
     * @param policy        execution policy
     * @param executionMode execution mode (FAST or DEEP)
     */
    public PipelineContext(
        String runId,
        String traceId,
        String sessionId,
        String userId,
        RagAnswerRequest request,
        ScopeMode scopeMode,
        ExecutionPolicy policy,
        String executionMode
    ) {
        this.runId = runId;
        this.traceId = traceId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.request = request;
        this.scopeMode = scopeMode;
        this.policy = policy;
        this.executionMode = executionMode;
        this.sseSeq = new AtomicLong(0);
        this.startTimeMs = System.currentTimeMillis();
        this.workingMemory = new ConcurrentHashMap<>();
    }
    
    // Getters for immutable fields
    
    public String runId() {
        return runId;
    }
    
    public String traceId() {
        return traceId;
    }
    
    public String sessionId() {
        return sessionId;
    }
    
    public String userId() {
        return userId;
    }
    
    public RagAnswerRequest request() {
        return request;
    }
    
    public ScopeMode scopeMode() {
        return scopeMode;
    }
    
    public ExecutionPolicy policy() {
        return policy;
    }
    
    public String executionMode() {
        return executionMode;
    }
    
    public AtomicLong sseSeq() {
        return sseSeq;
    }
    
    public long startTimeMs() {
        return startTimeMs;
    }
    
    /**
     * Get the next SSE sequence number.
     *
     * @return the next sequence number
     */
    public long nextSeq() {
        return sseSeq.incrementAndGet();
    }
    
    /**
     * Get elapsed time since pipeline start.
     *
     * @return elapsed time in milliseconds
     */
    public long elapsedMs() {
        return System.currentTimeMillis() - startTimeMs;
    }
    
    // Generic working memory methods
    
    /**
     * Get a value from working memory.
     *
     * @param key the key
     * @param <T> the expected type
     * @return the value, or null if not present
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) workingMemory.get(key);
    }
    
    /**
     * Put a value into working memory.
     *
     * @param key   the key
     * @param value the value
     */
    public void put(String key, Object value) {
        workingMemory.put(key, value);
    }
    
    /**
     * Get a value from working memory with a default.
     *
     * @param key          the key
     * @param defaultValue the default value
     * @param <T>          the expected type
     * @return the value, or defaultValue if not present
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(String key, T defaultValue) {
        Object value = workingMemory.get(key);
        return value != null ? (T) value : defaultValue;
    }
    
    /**
     * Check if a key exists in working memory.
     *
     * @param key the key
     * @return true if the key exists
     */
    public boolean has(String key) {
        return workingMemory.containsKey(key);
    }
    
    // Convenience methods for common data
    
    /**
     * Set extracted files in working memory.
     *
     * @param files the list of extracted files
     */
    public void setExtractedFiles(List<FileItem> files) {
        put(KEY_EXTRACTED_FILES, files);
    }
    
    /**
     * Get extracted files from working memory.
     *
     * @return the list of extracted files, or empty list if not set
     */
    @SuppressWarnings("unchecked")
    public List<FileItem> getExtractedFiles() {
        List<FileItem> files = get(KEY_EXTRACTED_FILES);
        return files != null ? files : List.of();
    }
    
    /**
     * Set RAG context in working memory.
     *
     * @param ragContext the RAG context text
     */
    public void setRagContext(String ragContext) {
        put(KEY_RAG_CONTEXT, ragContext);
    }
    
    /**
     * Get RAG context from working memory.
     *
     * @return the RAG context text, or empty string if not set
     */
    public String getRagContext() {
        String context = get(KEY_RAG_CONTEXT);
        return context != null ? context : "";
    }
    
    /**
     * Set final answer in working memory.
     *
     * @param answer the final answer
     */
    public void setFinalAnswer(String answer) {
        put(KEY_FINAL_ANSWER, answer);
    }
    
    /**
     * Get final answer from working memory.
     *
     * @return the final answer, or empty string if not set
     */
    public String getFinalAnswer() {
        String answer = get(KEY_FINAL_ANSWER);
        return answer != null ? answer : "";
    }
    
    /**
     * Builder for PipelineContext.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder class for PipelineContext.
     */
    public static class Builder {
        private String runId;
        private String traceId;
        private String sessionId;
        private String userId;
        private RagAnswerRequest request;
        private ScopeMode scopeMode;
        private ExecutionPolicy policy;
        private String executionMode;
        
        public Builder runId(String runId) {
            this.runId = runId;
            return this;
        }
        
        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }
        
        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }
        
        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }
        
        public Builder request(RagAnswerRequest request) {
            this.request = request;
            return this;
        }
        
        public Builder scopeMode(ScopeMode scopeMode) {
            this.scopeMode = scopeMode;
            return this;
        }
        
        public Builder policy(ExecutionPolicy policy) {
            this.policy = policy;
            return this;
        }
        
        public Builder executionMode(String executionMode) {
            this.executionMode = executionMode;
            return this;
        }
        
        public PipelineContext build() {
            return new PipelineContext(
                runId,
                traceId,
                sessionId,
                userId,
                request,
                scopeMode,
                policy,
                executionMode
            );
        }
    }
}
