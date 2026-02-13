package com.mrpot.agent.common.deep;

/**
 * A reflection note generated during reasoning.
 * Used to track contradictions, identify follow-up actions, and guide reasoning.
 * 
 * @param contradictionFlag whether this note identifies a contradiction
 * @param followupAction    suggested follow-up action to resolve issues
 * @param observation       the observation or insight noted
 * @param round             the round this reflection was made in
 * @param timestampMs       timestamp when this note was created
 */
public record ReflectionNote(
    boolean contradictionFlag,
    String followupAction,
    String observation,
    int round,
    long timestampMs
) {
    /**
     * Create a reflection note with current timestamp.
     */
    public static ReflectionNote of(boolean contradictionFlag, String followupAction, String observation, int round) {
        return new ReflectionNote(contradictionFlag, followupAction, observation, round, System.currentTimeMillis());
    }
    
    /**
     * Create a simple observation note.
     */
    public static ReflectionNote observation(String observation, int round) {
        return new ReflectionNote(false, null, observation, round, System.currentTimeMillis());
    }
    
    /**
     * Create a contradiction note.
     */
    public static ReflectionNote contradiction(String observation, String followupAction, int round) {
        return new ReflectionNote(true, followupAction, observation, round, System.currentTimeMillis());
    }
}
