package com.mrpot.agent.service.pipeline.artifacts;

/**
 * A reflection note produced during deep reasoning.
 * Used for self-correction and meta-cognitive analysis.
 * 
 * @param round       the reasoning round this reflection relates to
 * @param note        the reflection content (critique, observation, suggestion)
 * @param timestampMs timestamp when this reflection was generated
 */
public record ReflectionNote(
    int round,
    String note,
    long timestampMs
) {
    /**
     * Create a placeholder reflection note.
     */
    public static ReflectionNote placeholder(int round) {
        return new ReflectionNote(
            round,
            "Reflection pending",
            System.currentTimeMillis()
        );
    }
}
