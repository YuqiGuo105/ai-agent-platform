package com.mrpot.agent.service.pipeline;

import com.mrpot.agent.common.api.RagAnswerRequest;
import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.kb.KbDocument;
import com.mrpot.agent.common.kb.KbHit;
import com.mrpot.agent.common.policy.ExecutionPolicy;
import com.mrpot.agent.common.replay.ReplayMode;
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
    public static final String KEY_RAG_DOCS = "ragDocs";
    public static final String KEY_RAG_HITS = "ragHits";
    public static final String KEY_FINAL_ANSWER = "finalAnswer";
    public static final String KEY_HISTORY = "conversationHistory";
    public static final String KEY_USER_QUESTION = "userQuestion";
    public static final String KEY_POLICY = "executionPolicy";
    
    // Deep mode keys
    public static final String KEY_DEEP_PLAN = "deepPlan";
    public static final String KEY_DEEP_REASONING = "deepReasoning";
    public static final String KEY_DEEP_SYNTHESIS = "deepSynthesis";
    
    // Deep tool orchestration keys (Sprint 3)
    public static final String KEY_TOOL_CALL_HISTORY = "toolCallHistory";
    public static final String KEY_TOOL_EVIDENCE = "toolEvidence";
    public static final String KEY_TOOL_AUDIT = "toolAudit";
    
    // Deep verification and reflection keys (Sprint 4)
    public static final String KEY_VERIFICATION_REPORT = "verificationReport";
    public static final String KEY_REFLECTION_NOTE = "reflectionNote";
    public static final String KEY_NEEDS_ADDITIONAL_ROUND = "needsAdditionalRound";
    public static final String KEY_SYNTHESIS_BLOCKS = "synthesisBlocks";
    public static final String KEY_CURRENT_ROUND = "currentRound";
    
    // Complexity scoring keys (Sprint 6)
    public static final String KEY_COMPLEXITY_SCORE = "complexityScore";
    public static final String KEY_FEATURE_BREAKDOWN = "featureBreakdown";
    public static final String KEY_DEEP_ROUNDS_USED = "deepRoundsUsed";
    public static final String KEY_TOOL_CALLS_COUNT = "toolCallsCount";
    public static final String KEY_TOOL_SUCCESS_COUNT = "toolSuccessCount";
    
    // Replay keys (Sprint 7)
    public static final String KEY_PARENT_RUN_ID = "parentRunId";
    public static final String KEY_REPLAY_MODE = "replayMode";
    public static final String KEY_ALLOWED_TOOLS = "allowedTools";
    
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
     * Set RAG documents in working memory.
     *
     * @param docs the list of KB documents
     */
    public void setRagDocs(List<KbDocument> docs) {
        put(KEY_RAG_DOCS, docs);
    }

    /**
     * Get RAG documents from working memory.
     *
     * @return the list of KB documents, or empty list if not set
     */
    @SuppressWarnings("unchecked")
    public List<KbDocument> getRagDocs() {
        List<KbDocument> docs = get(KEY_RAG_DOCS);
        return docs != null ? docs : List.of();
    }

    /**
     * Set RAG hits (with scores) in working memory.
     *
     * @param hits the list of KB hits
     */
    public void setRagHits(List<KbHit> hits) {
        put(KEY_RAG_HITS, hits);
    }

    /**
     * Get RAG hits from working memory.
     *
     * @return the list of KB hits, or empty list if not set
     */
    @SuppressWarnings("unchecked")
    public List<KbHit> getRagHits() {
        List<KbHit> hits = get(KEY_RAG_HITS);
        return hits != null ? hits : List.of();
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
    
    // Deep mode helper methods
    
    /**
     * Set deep plan in working memory.
     *
     * @param plan the deep plan map
     */
    public void setDeepPlan(Map<String, Object> plan) {
        put(KEY_DEEP_PLAN, plan);
    }
    
    /**
     * Get deep plan from working memory.
     *
     * @return the deep plan map, or null if not set
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDeepPlan() {
        return get(KEY_DEEP_PLAN);
    }
    
    /**
     * Set deep reasoning result in working memory.
     *
     * @param reasoning the deep reasoning map
     */
    public void setDeepReasoning(Map<String, Object> reasoning) {
        put(KEY_DEEP_REASONING, reasoning);
    }
    
    /**
     * Get deep reasoning result from working memory.
     *
     * @return the deep reasoning map, or null if not set
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDeepReasoning() {
        return get(KEY_DEEP_REASONING);
    }
    
    /**
     * Set deep synthesis result in working memory.
     *
     * @param synthesis the deep synthesis map
     */
    public void setDeepSynthesis(Map<String, Object> synthesis) {
        put(KEY_DEEP_SYNTHESIS, synthesis);
    }
    
    /**
     * Get deep synthesis result from working memory.
     *
     * @return the deep synthesis map, or null if not set
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDeepSynthesis() {
        return get(KEY_DEEP_SYNTHESIS);
    }
    
    // Deep verification and reflection helper methods (Sprint 4)
    
    /**
     * Set verification report in working memory.
     *
     * @param report the verification report
     */
    public void setVerificationReport(com.mrpot.agent.common.deep.VerificationReport report) {
        put(KEY_VERIFICATION_REPORT, report);
    }
    
    /**
     * Get verification report from working memory.
     *
     * @return the verification report, or null if not set
     */
    public com.mrpot.agent.common.deep.VerificationReport getVerificationReport() {
        return get(KEY_VERIFICATION_REPORT);
    }
    
    /**
     * Set reflection note in working memory.
     *
     * @param note the reflection note
     */
    public void setReflectionNote(com.mrpot.agent.common.deep.ReflectionNote note) {
        put(KEY_REFLECTION_NOTE, note);
    }
    
    /**
     * Get reflection note from working memory.
     *
     * @return the reflection note, or null if not set
     */
    public com.mrpot.agent.common.deep.ReflectionNote getReflectionNote() {
        return get(KEY_REFLECTION_NOTE);
    }
    
    /**
     * Set whether an additional round is needed.
     *
     * @param needed true if additional round is needed
     */
    public void setNeedsAdditionalRound(boolean needed) {
        put(KEY_NEEDS_ADDITIONAL_ROUND, needed);
    }
    
    /**
     * Check if an additional round is needed.
     *
     * @return true if additional round is needed, false by default
     */
    public boolean needsAdditionalRound() {
        return getOrDefault(KEY_NEEDS_ADDITIONAL_ROUND, false);
    }
    
    /**
     * Set synthesis UI blocks in working memory.
     *
     * @param blocks the synthesis UI blocks
     */
    @SuppressWarnings("unchecked")
    public void setSynthesisBlocks(List<Map<String, Object>> blocks) {
        put(KEY_SYNTHESIS_BLOCKS, blocks);
    }
    
    /**
     * Get synthesis UI blocks from working memory.
     *
     * @return the synthesis UI blocks, or empty list if not set
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getSynthesisBlocks() {
        List<Map<String, Object>> blocks = get(KEY_SYNTHESIS_BLOCKS);
        return blocks != null ? blocks : List.of();
    }
    
    /**
     * Set current reasoning round.
     *
     * @param round the current round number
     */
    public void setCurrentRound(int round) {
        put(KEY_CURRENT_ROUND, round);
    }
    
    /**
     * Get current reasoning round.
     *
     * @return the current round number, 0 by default
     */
    public int getCurrentRound() {
        return getOrDefault(KEY_CURRENT_ROUND, 0);
    }
    
    // Complexity scoring helper methods (Sprint 6)
    
    /**
     * Set complexity score in working memory.
     *
     * @param score the complexity score (0.0-1.0)
     */
    public void setComplexityScore(double score) {
        put(KEY_COMPLEXITY_SCORE, score);
    }
    
    /**
     * Get complexity score from working memory.
     *
     * @return the complexity score, or 0.0 if not set
     */
    public double getComplexityScore() {
        Double score = get(KEY_COMPLEXITY_SCORE);
        return score != null ? score : 0.0;
    }
    
    /**
     * Set feature breakdown map in working memory.
     *
     * @param breakdown the feature breakdown map
     */
    public void setFeatureBreakdown(Map<String, Double> breakdown) {
        put(KEY_FEATURE_BREAKDOWN, breakdown);
    }
    
    /**
     * Get feature breakdown from working memory.
     *
     * @return the feature breakdown map, or empty map if not set
     */
    @SuppressWarnings("unchecked")
    public Map<String, Double> getFeatureBreakdown() {
        Map<String, Double> breakdown = get(KEY_FEATURE_BREAKDOWN);
        return breakdown != null ? breakdown : Map.of();
    }
    
    /**
     * Set deep rounds used count.
     *
     * @param rounds the number of deep rounds used
     */
    public void setDeepRoundsUsed(int rounds) {
        put(KEY_DEEP_ROUNDS_USED, rounds);
    }
    
    /**
     * Get deep rounds used count.
     *
     * @return the number of deep rounds used, 0 by default
     */
    public int getDeepRoundsUsed() {
        return getOrDefault(KEY_DEEP_ROUNDS_USED, 0);
    }
    
    /**
     * Increment and get tool call count.
     *
     * @return the new tool call count
     */
    public int incrementToolCallCount() {
        int count = getOrDefault(KEY_TOOL_CALLS_COUNT, 0) + 1;
        put(KEY_TOOL_CALLS_COUNT, count);
        return count;
    }
    
    /**
     * Get tool call count.
     *
     * @return the tool call count, 0 by default
     */
    public int getToolCallsCount() {
        return getOrDefault(KEY_TOOL_CALLS_COUNT, 0);
    }
    
    /**
     * Increment and get tool success count.
     *
     * @return the new tool success count
     */
    public int incrementToolSuccessCount() {
        int count = getOrDefault(KEY_TOOL_SUCCESS_COUNT, 0) + 1;
        put(KEY_TOOL_SUCCESS_COUNT, count);
        return count;
    }
    
    /**
     * Get tool success count.
     *
     * @return the tool success count, 0 by default
     */
    public int getToolSuccessCount() {
        return getOrDefault(KEY_TOOL_SUCCESS_COUNT, 0);
    }
    
    /**
     * Calculate tool success rate.
     *
     * @return the tool success rate (0.0-1.0), or 1.0 if no tools called
     */
    public double getToolSuccessRate() {
        int total = getToolCallsCount();
        if (total == 0) {
            return 1.0;
        }
        return (double) getToolSuccessCount() / total;
    }
    
    // Replay helper methods (Sprint 7)
    
    public void setParentRunId(String parentRunId) {
        put(KEY_PARENT_RUN_ID, parentRunId);
    }
    
    public String getParentRunId() {
        return get(KEY_PARENT_RUN_ID);
    }
    
    public void setReplayMode(ReplayMode mode) {
        put(KEY_REPLAY_MODE, mode);
    }
    
    public ReplayMode getReplayMode() {
        return get(KEY_REPLAY_MODE);
    }
    
    public void setAllowedTools(java.util.List<String> tools) {
        put(KEY_ALLOWED_TOOLS, tools);
    }
    
    @SuppressWarnings("unchecked")
    public java.util.List<String> getAllowedTools() {
        java.util.List<String> tools = get(KEY_ALLOWED_TOOLS);
        return tools != null ? tools : java.util.List.of();
    }
    
    public boolean isReplay() {
        return getReplayMode() != null;
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
        
        private String parentRunId;
        private ReplayMode replayMode;
        private java.util.List<String> allowedTools;
        
        public Builder parentRunId(String parentRunId) {
            this.parentRunId = parentRunId;
            return this;
        }
        
        public Builder replayMode(ReplayMode replayMode) {
            this.replayMode = replayMode;
            return this;
        }
        
        public Builder allowedTools(java.util.List<String> allowedTools) {
            this.allowedTools = allowedTools;
            return this;
        }
        
        public PipelineContext build() {
            PipelineContext ctx = new PipelineContext(
                runId,
                traceId,
                sessionId,
                userId,
                request,
                scopeMode,
                policy,
                executionMode
            );
            if (parentRunId != null) {
                ctx.setParentRunId(parentRunId);
            }
            if (replayMode != null) {
                ctx.setReplayMode(replayMode);
            }
            if (allowedTools != null) {
                ctx.setAllowedTools(allowedTools);
            }
            return ctx;
        }
    }
}
