package com.mrpot.agent.service.pipeline.stages;

import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.model.ChatMessage;
import com.mrpot.agent.service.LlmService;
import com.mrpot.agent.service.pipeline.DeepArtifactStore;
import com.mrpot.agent.service.pipeline.DeepModeConfig;
import com.mrpot.agent.service.pipeline.PipelineContext;
import com.mrpot.agent.service.pipeline.Processor;
import com.mrpot.agent.service.pipeline.artifacts.DeepPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deep plan stage - creates a planning structure for deep reasoning.
 * 
 * Uses LLM to generate a structured plan based on user question and context.
 * Emits DEEP_PLAN_START and DEEP_PLAN_DONE SSE events.
 * Falls back to simple plan on timeout or error.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeepPlanStage implements Processor<Void, SseEnvelope> {
    
    private static final String PLAN_PROMPT_TEMPLATE = """
        Analyze this question and create a reasoning plan.
        
        Question: %s
        
        Respond in this exact format:
        OBJECTIVE: <main goal in one sentence>
        CONSTRAINTS: <comma-separated constraints, or NONE>
        SUBTASKS: <comma-separated subtasks to accomplish>
        SUCCESS_CRITERIA: <comma-separated criteria for completion>
        """;
    
    // Patterns to parse LLM response
    private static final Pattern OBJECTIVE_PATTERN = Pattern.compile("OBJECTIVE:\\s*(.+?)(?=\\n|CONSTRAINTS|$)", Pattern.DOTALL);
    private static final Pattern CONSTRAINTS_PATTERN = Pattern.compile("CONSTRAINTS:\\s*(.+?)(?=\\n|SUBTASKS|$)", Pattern.DOTALL);
    private static final Pattern SUBTASKS_PATTERN = Pattern.compile("SUBTASKS:\\s*(.+?)(?=\\n|SUCCESS_CRITERIA|$)", Pattern.DOTALL);
    private static final Pattern SUCCESS_CRITERIA_PATTERN = Pattern.compile("SUCCESS_CRITERIA:\\s*(.+?)$", Pattern.DOTALL);
    
    private final LlmService llmService;
    private final DeepModeConfig config;
    
    @Override
    public Mono<SseEnvelope> process(Void input, PipelineContext context) {
        log.debug("Starting deep plan stage for runId={}", context.runId());
        
        // Get question from request, fallback to working memory for tests
        String question = getQuestion(context);
        if (question == null || question.isBlank()) {
            log.warn("No user question found for runId={}, using fallback plan", context.runId());
            return createFallbackResponse(context, "empty question");
        }
        
        // Get conversation history from context
        List<ChatMessage> historyRaw = context.get(PipelineContext.KEY_HISTORY);
        final List<ChatMessage> history = historyRaw != null ? historyRaw : List.of();
        
        String prompt = String.format(PLAN_PROMPT_TEMPLATE, question);
        
        // Call LLM to generate plan and emit DEEP_PLAN_DONE
        return llmService.streamResponse(prompt, history, "DEEP")
            .reduce(new StringBuilder(), StringBuilder::append)
            .map(StringBuilder::toString)
            .timeout(Duration.ofSeconds(config.getPlanTimeoutSeconds()))
            .map(response -> parsePlan(response, question))
            .doOnNext(plan -> {
                // Store plan in artifact store
                DeepArtifactStore store = new DeepArtifactStore(context);
                store.setPlan(plan);
                log.info("Deep plan created for runId={}: objective='{}', subtasks={}", 
                    context.runId(), 
                    truncate(plan.objective(), 50),
                    plan.subtasks().size());
            })
            .map(plan -> createDoneEnvelope(context, plan))
            .onErrorResume(e -> {
                log.error("Failed to create deep plan for runId={}: {}", 
                    context.runId(), e.getMessage(), e);
                
                // Use fallback plan
                DeepPlan fallback = DeepPlan.fallback(question);
                DeepArtifactStore store = new DeepArtifactStore(context);
                store.setPlan(fallback);
                
                return Mono.just(createDoneEnvelope(context, fallback, "fallback"));
            });
    }
    
    /**
     * Parse LLM response into DeepPlan.
     */
    private DeepPlan parsePlan(String response, String question) {
        String objective = extractGroup(OBJECTIVE_PATTERN, response, "Answer: " + truncate(question, 80));
        List<String> constraints = parseCommaSeparated(extractGroup(CONSTRAINTS_PATTERN, response, ""));
        List<String> subtasks = parseCommaSeparated(extractGroup(SUBTASKS_PATTERN, response, "Analyze and respond"));
        List<String> successCriteria = parseCommaSeparated(extractGroup(SUCCESS_CRITERIA_PATTERN, response, "Provide accurate answer"));
        
        // Filter out NONE values
        constraints = constraints.stream().filter(c -> !c.equalsIgnoreCase("NONE")).toList();
        
        // Ensure at least one subtask
        if (subtasks.isEmpty()) {
            subtasks = List.of("Direct response");
        }
        
        // Ensure at least one success criterion
        if (successCriteria.isEmpty()) {
            successCriteria = List.of("Provide helpful answer");
        }
        
        return new DeepPlan(objective, constraints, subtasks, successCriteria);
    }
    
    private String extractGroup(Pattern pattern, String text, String defaultValue) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String value = matcher.group(1).trim();
            return value.isEmpty() ? defaultValue : value;
        }
        return defaultValue;
    }
    
    private List<String> parseCommaSeparated(String value) {
        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }
    
    private SseEnvelope createDoneEnvelope(PipelineContext context, DeepPlan plan) {
        return createDoneEnvelope(context, plan, "complete");
    }
    
    /**
     * Create the done envelope for deep plan stage.
     * Provides user-friendly display with structured planning data:
     * - Metadata for UI routing (displayName, uiComponent, emoji)
     * - Organized planning info with objective preview
     * - Enriched subtasks, constraints, and success criteria with IDs and metadata
     * - Summary statistics for quick reference
     */
    private SseEnvelope createDoneEnvelope(PipelineContext context, DeepPlan plan, String status) {
        var metadata = StageNames.getMetadata(StageNames.DEEP_PLAN_DONE);
        
        // Build enriched subtasks with IDs and ordering
        List<Map<String, Object>> subtasksList = new ArrayList<>();
        List<String> subtasks = plan.subtasks();
        for (int i = 0; i < subtasks.size(); i++) {
            subtasksList.add(Map.of(
                "id", "task-" + (i + 1),
                "title", subtasks.get(i),
                "completed", false,
                "order", i + 1
            ));
        }
        
        // Build enriched constraints with IDs and severity
        List<Map<String, Object>> constraintsList = new ArrayList<>();
        List<String> constraints = plan.constraints();
        for (int i = 0; i < constraints.size(); i++) {
            constraintsList.add(Map.of(
                "id", "constraint-" + (i + 1),
                "text", constraints.get(i),
                "severity", "high"
            ));
        }
        
        // Build enriched success criteria with IDs and status
        List<Map<String, Object>> criteriaList = new ArrayList<>();
        List<String> criteria = plan.successCriteria();
        for (int i = 0; i < criteria.size(); i++) {
            criteriaList.add(Map.of(
                "id", "criteria-" + (i + 1),
                "text", criteria.get(i),
                "met", false
            ));
        }
        
        return new SseEnvelope(
            StageNames.DEEP_PLAN_DONE,
            String.format("%s %s", metadata.emoji(), metadata.displayName()),
            Map.ofEntries(
                // UI Metadata for frontend routing
                Map.entry("uiComponent", metadata.uiComponent()),
                Map.entry("displayName", metadata.displayName()),
                Map.entry("description", metadata.description()),
                
                // Core planning data
                Map.entry("planning", Map.of(
                    "status", status,
                    "objective", plan.objective(),
                    "objectivePreview", truncate(plan.objective(), 100)
                )),
                
                // Enriched subtasks
                Map.entry("subtasks", subtasksList),
                
                // Enriched constraints
                Map.entry("constraints", constraintsList),
                
                // Enriched success criteria
                Map.entry("successCriteria", criteriaList),
                
                // Summary statistics
                Map.entry("summary", Map.of(
                    "totalSubtasks", subtasks.size(),
                    "totalConstraints", constraints.size(),
                    "estimatedDuration", "5-10 minutes",
                    "complexity", "medium"
                ))
            ),
            context.nextSeq(),
            System.currentTimeMillis(),
            context.traceId(),
            context.sessionId()
        );
    }
    
    private Mono<SseEnvelope> createFallbackResponse(PipelineContext context, String reason) {
        DeepPlan fallback = DeepPlan.fallback("");
        DeepArtifactStore store = new DeepArtifactStore(context);
        store.setPlan(fallback);
        
        var metadata = StageNames.getMetadata(StageNames.DEEP_PLAN_DONE);
        
        // Build enriched subtasks
        List<Map<String, Object>> subtasksList = new ArrayList<>();
        List<String> subtasks = fallback.subtasks();
        for (int i = 0; i < subtasks.size(); i++) {
            subtasksList.add(Map.of(
                "id", "task-" + (i + 1),
                "title", subtasks.get(i),
                "completed", false,
                "order", i + 1
            ));
        }
        
        return Mono.just(new SseEnvelope(
            StageNames.DEEP_PLAN_DONE,
            String.format("%s %s (fallback: %s)", metadata.emoji(), metadata.displayName(), reason),
            Map.ofEntries(
                Map.entry("uiComponent", metadata.uiComponent()),
                Map.entry("displayName", metadata.displayName()),
                Map.entry("description", metadata.description()),
                Map.entry("planning", Map.of(
                    "status", "fallback",
                    "reason", reason
                )),
                Map.entry("subtasks", subtasksList),
                Map.entry("constraints", fallback.constraints()),
                Map.entry("successCriteria", fallback.successCriteria()),
                Map.entry("summary", Map.of(
                    "totalSubtasks", fallback.subtasks().size(),
                    "totalConstraints", fallback.constraints().size()
                ))
            ),
            context.nextSeq(),
            System.currentTimeMillis(),
            context.traceId(),
            context.sessionId()
        ));
    }
    
    /**
     * Get user question from context request or working memory.
     */
    private String getQuestion(PipelineContext context) {
        // Try request first
        if (context.request() != null && context.request().question() != null) {
            return context.request().question();
        }
        // Fallback to working memory (for tests)
        return context.get(PipelineContext.KEY_USER_QUESTION);
    }
    
    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
