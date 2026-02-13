package com.mrpot.agent.common.deep;

import java.util.List;

/**
 * A single step in the deep reasoning process.
 * 
 * @param hypothesis    the current hypothesis or answer being considered
 * @param evidenceRefs  references to evidence supporting this hypothesis
 * @param confidence    confidence level (0.0 to 1.0)
 * @param round         the round number this step belongs to
 * @param timestampMs   timestamp when this step was created
 */
public record ReasoningStep(
    String hypothesis,
    List<String> evidenceRefs,
    double confidence,
    int round,
    long timestampMs
) {
    /**
     * Create a reasoning step with current timestamp.
     */
    public static ReasoningStep of(String hypothesis, List<String> evidenceRefs, double confidence, int round) {
        return new ReasoningStep(hypothesis, evidenceRefs, confidence, round, System.currentTimeMillis());
    }
    
    /**
     * Create an initial reasoning step.
     */
    public static ReasoningStep initial(String hypothesis) {
        return new ReasoningStep(hypothesis, List.of(), 0.5, 0, System.currentTimeMillis());
    }
}
