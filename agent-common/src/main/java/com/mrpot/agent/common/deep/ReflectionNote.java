package com.mrpot.agent.common.deep;

/**
 * A reflection note generated during reasoning verification.
 * Used to track contradictions, identify follow-up actions, and guide reasoning.
 * 
 * @param contradictionFlag whether this note identifies a contradiction
 * @param followupAction    suggested follow-up action ("continue", "retry", "proceed")
 * @param observation       the observation or insight noted (detailed)
 * @param summary           brief summary for SSE output
 * @param round             the round this reflection was made in
 * @param timestampMs       timestamp when this note was created
 */
public record ReflectionNote(
    boolean contradictionFlag,
    String followupAction,
    String observation,
    String summary,
    int round,
    long timestampMs
) {
    /**
     * Create a reflection note with current timestamp.
     */
    public static ReflectionNote of(boolean contradictionFlag, String followupAction, String observation, String summary, int round) {
        return new ReflectionNote(contradictionFlag, followupAction, observation, summary, round, System.currentTimeMillis());
    }
    
    /**
     * Create a simple observation note.
     */
    public static ReflectionNote observation(String observation, int round) {
        return new ReflectionNote(false, "proceed", observation, observation, round, System.currentTimeMillis());
    }
    
    /**
     * Create a contradiction note that triggers retry.
     */
    public static ReflectionNote contradiction(String observation, String followupAction, int round) {
        return new ReflectionNote(true, followupAction, observation, "Contradiction detected: " + observation, round, System.currentTimeMillis());
    }
    
    /**
     * Create a default reflection note for error recovery.
     */
    public static ReflectionNote defaultNote(int round) {
        return new ReflectionNote(false, "proceed", "Default reflection due to error", "Proceeding with default", round, System.currentTimeMillis());
    }
}
