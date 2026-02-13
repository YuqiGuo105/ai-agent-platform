package com.mrpot.agent.common.deep;

import java.util.List;

/**
 * Deep planning result containing the high-level plan for reasoning.
 * 
 * @param objective       the main objective derived from user question
 * @param constraints     any constraints or limitations to consider
 * @param subtasks        list of subtasks to accomplish the objective
 * @param successCriteria criteria for determining when reasoning is complete
 */
public record DeepPlan(
    String objective,
    List<String> constraints,
    List<String> subtasks,
    List<String> successCriteria
) {
    /**
     * Create a fallback plan when planning fails.
     */
    public static DeepPlan fallback(String question) {
        return new DeepPlan(
            "Answer: " + truncate(question, 100),
            List.of(),
            List.of("Direct response"),
            List.of("Provide helpful answer")
        );
    }
    
    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
