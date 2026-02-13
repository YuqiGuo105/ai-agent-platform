package com.mrpot.agent.service.pipeline;

import com.mrpot.agent.service.pipeline.artifacts.DeepPlan;
import com.mrpot.agent.service.pipeline.artifacts.ReasoningStep;
import com.mrpot.agent.service.pipeline.artifacts.ReflectionNote;
import com.mrpot.agent.service.pipeline.artifacts.ToolCallRecord;
import com.mrpot.agent.service.pipeline.artifacts.VerificationReport;

import java.util.ArrayList;
import java.util.List;

/**
 * Typed helper class for accessing DEEP mode artifacts in PipelineContext.
 * Provides type-safe methods for storing and retrieving artifacts.
 */
public class DeepArtifactStore {
    
    // Key constants for working memory
    public static final String KEY_DEEP_PLAN = "deepPlan";
    public static final String KEY_REASONING_STEPS = "reasoningSteps";
    public static final String KEY_REFLECTION_NOTES = "reflectionNotes";
    public static final String KEY_TOOL_CALLS = "toolCalls";
    public static final String KEY_VERIFICATION_REPORT = "verificationReport";
    public static final String KEY_ACCUMULATED_REASONING = "accumulatedReasoning";
    
    private final PipelineContext context;
    
    public DeepArtifactStore(PipelineContext context) {
        this.context = context;
    }
    
    // ===== DeepPlan =====
    
    public void setPlan(DeepPlan plan) {
        context.put(KEY_DEEP_PLAN, plan);
    }
    
    public DeepPlan getPlan() {
        return context.get(KEY_DEEP_PLAN);
    }
    
    // ===== ReasoningStep =====
    
    public void addReasoningStep(ReasoningStep step) {
        List<ReasoningStep> steps = getReasoningSteps();
        List<ReasoningStep> updated = new ArrayList<>(steps);
        updated.add(step);
        context.put(KEY_REASONING_STEPS, updated);
    }
    
    @SuppressWarnings("unchecked")
    public List<ReasoningStep> getReasoningSteps() {
        List<ReasoningStep> steps = context.get(KEY_REASONING_STEPS);
        return steps != null ? steps : new ArrayList<>();
    }
    
    public ReasoningStep getLastReasoningStep() {
        List<ReasoningStep> steps = getReasoningSteps();
        return steps.isEmpty() ? null : steps.get(steps.size() - 1);
    }
    
    public int getReasoningStepCount() {
        return getReasoningSteps().size();
    }
    
    // ===== ReflectionNote =====
    
    public void addReflectionNote(ReflectionNote note) {
        List<ReflectionNote> notes = getReflectionNotes();
        List<ReflectionNote> updated = new ArrayList<>(notes);
        updated.add(note);
        context.put(KEY_REFLECTION_NOTES, updated);
    }
    
    @SuppressWarnings("unchecked")
    public List<ReflectionNote> getReflectionNotes() {
        List<ReflectionNote> notes = context.get(KEY_REFLECTION_NOTES);
        return notes != null ? notes : new ArrayList<>();
    }
    
    // ===== ToolCallRecord =====
    
    public void addToolCall(ToolCallRecord toolCall) {
        List<ToolCallRecord> calls = getToolCalls();
        List<ToolCallRecord> updated = new ArrayList<>(calls);
        updated.add(toolCall);
        context.put(KEY_TOOL_CALLS, updated);
    }
    
    @SuppressWarnings("unchecked")
    public List<ToolCallRecord> getToolCalls() {
        List<ToolCallRecord> calls = context.get(KEY_TOOL_CALLS);
        return calls != null ? calls : new ArrayList<>();
    }
    
    // ===== VerificationReport =====
    
    public void setVerificationReport(VerificationReport report) {
        context.put(KEY_VERIFICATION_REPORT, report);
    }
    
    public VerificationReport getVerificationReport() {
        return context.get(KEY_VERIFICATION_REPORT);
    }
    
    // ===== Accumulated Reasoning (raw text from LLM) =====
    
    public void appendReasoning(String text) {
        String current = getAccumulatedReasoning();
        context.put(KEY_ACCUMULATED_REASONING, current + text);
    }
    
    public String getAccumulatedReasoning() {
        String reasoning = context.get(KEY_ACCUMULATED_REASONING);
        return reasoning != null ? reasoning : "";
    }
    
    public void clearAccumulatedReasoning() {
        context.put(KEY_ACCUMULATED_REASONING, "");
    }
    
    // ===== Utility methods =====
    
    /**
     * Get the current confidence level from the last reasoning step.
     * 
     * @return confidence between 0.0 and 1.0, or 0.0 if no steps exist
     */
    public double getCurrentConfidence() {
        ReasoningStep lastStep = getLastReasoningStep();
        return lastStep != null ? lastStep.confidence() : 0.0;
    }
    
    /**
     * Check if reasoning has made progress since the last step.
     * Uses simple string similarity to detect no-progress scenarios.
     * 
     * @param newHypothesis the new hypothesis to compare
     * @return true if there is meaningful progress
     */
    public boolean hasProgress(String newHypothesis) {
        ReasoningStep lastStep = getLastReasoningStep();
        if (lastStep == null || lastStep.hypothesis() == null) {
            return true; // First step always counts as progress
        }
        
        String lastHypothesis = lastStep.hypothesis();
        if (newHypothesis == null || newHypothesis.isBlank()) {
            return false;
        }
        
        // Simple similarity check: if more than 90% similar, no progress
        double similarity = calculateSimilarity(lastHypothesis, newHypothesis);
        return similarity < 0.9;
    }
    
    private double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equals(s2)) return 1.0;
        
        // Simple word overlap similarity
        String[] words1 = s1.toLowerCase().split("\\s+");
        String[] words2 = s2.toLowerCase().split("\\s+");
        
        int overlap = 0;
        for (String w1 : words1) {
            for (String w2 : words2) {
                if (w1.equals(w2)) {
                    overlap++;
                    break;
                }
            }
        }
        
        int maxWords = Math.max(words1.length, words2.length);
        return maxWords > 0 ? (double) overlap / maxWords : 0.0;
    }
}
