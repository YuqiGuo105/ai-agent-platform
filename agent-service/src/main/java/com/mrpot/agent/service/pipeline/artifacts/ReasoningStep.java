package com.mrpot.agent.service.pipeline.artifacts;

import java.util.List;

/**
 * A single reasoning step produced during deep reasoning.
 * 
 * @param round        the reasoning round number (1-based)
 * @param hypothesis   the current hypothesis or conclusion
 * @param evidenceRefs references to supporting evidence (KB hits, file content, etc.)
 * @param confidence   confidence score between 0.0 and 1.0
 * @param timestampMs  timestamp when this step was completed
 */
public record ReasoningStep(
    int round,
    String hypothesis,
    List<String> evidenceRefs,
    double confidence,
    long timestampMs
) {
    /**
     * Create a simple reasoning step with minimal info.
     */
    public static ReasoningStep simple(int round, String hypothesis, double confidence) {
        return new ReasoningStep(
            round,
            hypothesis,
            List.of(),
            confidence,
            System.currentTimeMillis()
        );
    }
}
