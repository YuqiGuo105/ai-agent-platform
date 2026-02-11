package com.mrpot.agent.service.policy;

import com.mrpot.agent.common.api.RagAnswerRequest;
import com.mrpot.agent.common.policy.ExecutionPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides the execution mode (FAST or DEEP) based on request and policy.
 * Uses heuristics to determine question complexity and user intent.
 */
@Slf4j
@Component
public class ModeDecider {
    
    private static final String MODE_FAST = "FAST";
    private static final String MODE_DEEP = "DEEP";
    private static final String REQUEST_MODE_DEEPTHINKING = "DEEPTHINKING";
    
    // Keywords indicating complex questions (English and Chinese)
    private static final Set<String> COMPLEX_KEYWORDS = Set.of(
        "compare", "对比", "比较",
        "analyze", "分析",
        "why", "为什么",
        "explain", "解释", "说明",
        "evaluate", "评估",
        "difference", "区别", "不同",
        "relationship", "关系",
        "pros and cons", "优缺点", "利弊",
        "recommend", "建议", "推荐",
        "how to", "如何", "怎么", "怎样"
    );
    
    // Minimum question length for complexity consideration
    private static final int MIN_COMPLEX_LENGTH = 20;
    
    // Pattern to count question marks
    private static final Pattern QUESTION_MARK_PATTERN = Pattern.compile("[?？]");
    
    /**
     * Decide the execution mode based on request and policy.
     *
     * Logic:
     * 1. If request mode is "DEEPTHINKING" and policy allows (maxToolRounds >= 3), return "DEEP"
     * 2. If question is complex (heuristic) and policy allows, return "DEEP"
     * 3. Otherwise return policy's preferred mode
     *
     * @param request the RAG answer request
     * @param policy  the execution policy
     * @return "FAST" or "DEEP" execution mode
     */
    public String decide(RagAnswerRequest request, ExecutionPolicy policy) {
        String requestMode = request.resolveMode();
        String question = request.question();
        
        // Check if request explicitly asks for deep thinking
        if (REQUEST_MODE_DEEPTHINKING.equalsIgnoreCase(requestMode)) {
            if (policy.maxToolRounds() >= 3) {
                log.debug("Using DEEP mode: explicit DEEPTHINKING request with sufficient tool rounds");
                return MODE_DEEP;
            }
            log.debug("DEEPTHINKING requested but policy maxToolRounds={}, using preferred mode", 
                policy.maxToolRounds());
        }
        
        // Check if question complexity warrants DEEP mode
        if (isComplexQuestion(question) && policy.maxToolRounds() >= 3) {
            log.debug("Using DEEP mode: complex question detected");
            return MODE_DEEP;
        }
        
        // Default to policy's preferred mode
        String preferredMode = policy.preferredMode();
        log.debug("Using policy preferred mode: {}", preferredMode);
        return preferredMode;
    }
    
    /**
     * Determine if a question is complex based on heuristics.
     *
     * Checks for:
     * - Keywords indicating analytical or comparative questions
     * - Multiple question marks (compound questions)
     * - Minimum question length
     *
     * @param question the question text
     * @return true if the question is considered complex
     */
    private boolean isComplexQuestion(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        
        // Check minimum length
        if (question.length() < MIN_COMPLEX_LENGTH) {
            return false;
        }
        
        String lowerQuestion = question.toLowerCase();
        
        // Check for complex keywords
        for (String keyword : COMPLEX_KEYWORDS) {
            if (lowerQuestion.contains(keyword.toLowerCase())) {
                log.trace("Complex keyword found: {}", keyword);
                return true;
            }
        }
        
        // Check for multiple question marks (compound questions)
        long questionMarkCount = QUESTION_MARK_PATTERN.matcher(question).results().count();
        if (questionMarkCount >= 2) {
            log.trace("Multiple question marks detected: {}", questionMarkCount);
            return true;
        }
        
        return false;
    }
}
