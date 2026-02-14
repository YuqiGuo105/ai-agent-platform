package com.mrpot.agent.service.pipeline.stages;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrpot.agent.common.deep.ToolCallRecord;
import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.common.tool.mcp.CallToolRequest;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.service.DeepToolAuditService;
import com.mrpot.agent.service.ToolInvoker;
import com.mrpot.agent.service.pipeline.DeepArtifactStore;
import com.mrpot.agent.service.pipeline.PipelineContext;
import com.mrpot.agent.service.pipeline.Processor;
import com.mrpot.agent.service.pipeline.artifacts.DeepPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Deep Tool Orchestration Stage.
 * Determines whether to call tools based on planning.next_step output,
 * executes tools via MCP, and normalizes results.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeepToolOrchestrationStage implements Processor<Void, SseEnvelope> {
    
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int MAX_TOOLS_PER_ROUND = 3;
    
    private final ToolInvoker toolInvoker;
    private final DeepToolAuditService auditService;
    
    @Override
    public Mono<SseEnvelope> process(Void input, PipelineContext context) {
        log.debug("Starting deep tool orchestration for runId={}", context.runId());
        
        DeepArtifactStore store = new DeepArtifactStore(context);
        DeepPlan plan = store.getPlan();
        
        // Check if tool orchestration is needed
        if (plan == null || !shouldCallTools(context, plan)) {
            log.info("Tool orchestration skipped for runId={}: no tools needed", context.runId());
            return Mono.just(createSkipEnvelope(context, "No tools required"));
        }
        
        // Determine which tools to call based on current state
        List<ToolCallIntent> toolIntents = determineToolCalls(context, plan);
        
        if (toolIntents.isEmpty()) {
            return Mono.just(createSkipEnvelope(context, "No tool calls determined"));
        }
        
        // Get current round from reasoning steps
        int currentRound = store.getReasoningStepCount();
        
        // Execute tool calls sequentially (up to MAX_TOOLS_PER_ROUND)
        return executeToolCalls(context, toolIntents, currentRound)
            .map(results -> createResultEnvelope(context, results))
            .onErrorResume(e -> {
                log.error("Tool orchestration failed for runId={}: {}", 
                    context.runId(), e.getMessage(), e);
                auditService.recordError(context, "orchestration", e.getMessage(), currentRound);
                return Mono.just(createErrorEnvelope(context, e.getMessage()));
            });
    }
    
    /**
     * Determine if tools should be called based on plan and context.
     */
    private boolean shouldCallTools(PipelineContext context, DeepPlan plan) {
        // Check policy allows tool calls
        if (context.policy() != null && context.policy().maxToolRounds() <= 0) {
            return false;
        }
        
        // Check if plan has subtasks that might need tools
        if (plan.subtasks().isEmpty()) {
            return false;
        }
        
        // Check if we've exceeded max tool rounds
        @SuppressWarnings("unchecked")
        List<ToolCallRecord> history = context.get(PipelineContext.KEY_TOOL_CALL_HISTORY);
        if (history != null && context.policy() != null) {
            int maxRounds = context.policy().maxToolRounds();
            long completedRounds = history.stream()
                .map(ToolCallRecord::timestampMs)
                .distinct()
                .count();
            if (completedRounds >= maxRounds) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Determine which tools to call based on current state and plan.
     */
    private List<ToolCallIntent> determineToolCalls(PipelineContext context, DeepPlan plan) {
        List<ToolCallIntent> intents = new ArrayList<>();
        
        // Simple heuristic: check subtasks for keywords that map to tools
        for (String subtask : plan.subtasks()) {
            String subtaskLower = subtask.toLowerCase();
            
            if (subtaskLower.contains("analyze") || subtaskLower.contains("examine")) {
                intents.add(new ToolCallIntent(
                    "reasoning.analyze",
                    Map.of("data", subtask, "question", plan.objective())
                ));
            } else if (subtaskLower.contains("compare") || subtaskLower.contains("contrast")) {
                intents.add(new ToolCallIntent(
                    "reasoning.compare",
                    Map.of("items", List.of(subtask), "criteria", plan.objective())
                ));
            } else if (subtaskLower.contains("remember") || subtaskLower.contains("store")) {
                intents.add(new ToolCallIntent(
                    "memory.store",
                    Map.of("lane", "facts", "key", "subtask_" + intents.size(), "value", subtask, "ttl", 1800)
                ));
            } else if (subtaskLower.contains("recall") || subtaskLower.contains("retrieve")) {
                intents.add(new ToolCallIntent(
                    "memory.recall",
                    Map.of("lane", "facts", "key", "context")
                ));
            }
            
            // Limit tools per round
            if (intents.size() >= MAX_TOOLS_PER_ROUND) {
                break;
            }
        }
        
        return intents;
    }
    
    /**
     * Execute tool calls sequentially and collect results.
     */
    private Mono<List<NormalizedToolResult>> executeToolCalls(
            PipelineContext context, 
            List<ToolCallIntent> intents, 
            int round) {
        
        List<NormalizedToolResult> results = Collections.synchronizedList(new ArrayList<>());
        
        Mono<Void> chain = Mono.empty();
        
        for (ToolCallIntent intent : intents) {
            chain = chain.then(
                executeToolCall(context, intent, round)
                    .doOnNext(results::add)
                    .then()
            );
        }
        
        return chain.thenReturn(results);
    }
    
    /**
     * Execute a single tool call.
     */
    private Mono<NormalizedToolResult> executeToolCall(
            PipelineContext context, 
            ToolCallIntent intent, 
            int round) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            ObjectNode argsNode = mapper.valueToTree(intent.args());
            
            CallToolRequest request = new CallToolRequest(
                intent.toolName(),
                argsNode,
                null,
                null,
                context.traceId(),
                context.sessionId()
            );
            
            return toolInvoker.call(request, context.runId())
                .map(response -> normalizeResponse(intent, response, startTime))
                .doOnNext(result -> {
                    // Track in audit service
                    ToolCallRecord record = result.success()
                        ? ToolCallRecord.success(intent.toolName(), intent.args(), result.data(), result.latencyMs())
                        : ToolCallRecord.failure(intent.toolName(), intent.args(), result.error(), result.latencyMs());
                    
                    auditService.trackToolCall(context, record, round);
                    
                    // Add to tool call history
                    addToToolCallHistory(context, record);
                    
                    // Add to evidence if successful
                    if (result.success()) {
                        addToEvidence(context, intent.toolName(), result.data());
                    }
                })
                .onErrorResume(e -> {
                    long latency = System.currentTimeMillis() - startTime;
                    NormalizedToolResult errorResult = new NormalizedToolResult(
                        intent.toolName(),
                        false,
                        null,
                        e.getMessage() != null ? e.getMessage() : "Unknown error",
                        latency
                    );
                    
                    ToolCallRecord record = ToolCallRecord.failure(
                        intent.toolName(), intent.args(), errorResult.error(), latency
                    );
                    auditService.trackToolCall(context, record, round);
                    addToToolCallHistory(context, record);
                    
                    return Mono.just(errorResult);
                });
                
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            return Mono.just(new NormalizedToolResult(
                intent.toolName(),
                false,
                null,
                e.getMessage() != null ? e.getMessage() : "Unknown error",
                latency
            ));
        }
    }
    
    /**
     * Normalize CallToolResponse to unified format.
     */
    private NormalizedToolResult normalizeResponse(
            ToolCallIntent intent, 
            CallToolResponse response, 
            long startTime) {
        
        long latency = System.currentTimeMillis() - startTime;
        
        if (response == null) {
            return new NormalizedToolResult(intent.toolName(), false, null, "Null response", latency);
        }
        
        if (!response.ok()) {
            String error = response.error() != null ? response.error().message() : "Tool call failed";
            return new NormalizedToolResult(intent.toolName(), false, null, error, latency);
        }
        
        String data = response.result() != null ? response.result().toString() : "";
        return new NormalizedToolResult(intent.toolName(), true, data, null, latency);
    }
    
    /**
     * Add tool call record to history.
     */
    private void addToToolCallHistory(PipelineContext context, ToolCallRecord record) {
        @SuppressWarnings("unchecked")
        List<ToolCallRecord> history = context.getOrDefault(
            PipelineContext.KEY_TOOL_CALL_HISTORY, 
            Collections.synchronizedList(new ArrayList<>())
        );
        history.add(record);
        context.put(PipelineContext.KEY_TOOL_CALL_HISTORY, history);
    }
    
    /**
     * Add successful tool result to evidence.
     */
    private void addToEvidence(PipelineContext context, String toolName, String data) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> evidence = context.getOrDefault(
            PipelineContext.KEY_TOOL_EVIDENCE,
            Collections.synchronizedList(new ArrayList<>())
        );
        evidence.add(Map.of(
            "source", toolName,
            "data", data != null ? truncate(data, 500) : "",
            "timestamp", String.valueOf(System.currentTimeMillis())
        ));
        context.put(PipelineContext.KEY_TOOL_EVIDENCE, evidence);
    }
    
    private SseEnvelope createResultEnvelope(PipelineContext context, List<NormalizedToolResult> results) {
        int successCount = (int) results.stream().filter(NormalizedToolResult::success).count();
        long totalLatency = results.stream().mapToLong(NormalizedToolResult::latencyMs).sum();
        
        return new SseEnvelope(
            StageNames.DEEP_TOOL_ORCH_DONE,
            "Stage: Tool orchestration complete - " + successCount + " of " + results.size() + " tools succeeded",
            Map.of(
                "toolCount", results.size(),
                "successCount", successCount,
                "failureCount", results.size() - successCount,
                "totalLatencyMs", totalLatency,
                "tools", results.stream()
                    .map(r -> Map.of(
                        "name", r.toolName(),
                        "success", r.success(),
                        "latency", r.latencyMs()
                    ))
                    .toList()
            ),
            context.nextSeq(),
            System.currentTimeMillis(),
            context.traceId(),
            context.sessionId()
        );
    }
    
    private SseEnvelope createSkipEnvelope(PipelineContext context, String reason) {
        return new SseEnvelope(
            StageNames.DEEP_TOOL_ORCH_DONE,
            "Stage: Tool orchestration skipped (" + reason + ")",
            Map.of("skipped", true, "reason", reason),
            context.nextSeq(),
            System.currentTimeMillis(),
            context.traceId(),
            context.sessionId()
        );
    }
    
    private SseEnvelope createErrorEnvelope(PipelineContext context, String error) {
        return new SseEnvelope(
            StageNames.DEEP_TOOL_ORCH_DONE,
            "Stage: Tool orchestration failed",
            Map.of("success", false, "error", error != null ? error : "Unknown error"),
            context.nextSeq(),
            System.currentTimeMillis(),
            context.traceId(),
            context.sessionId()
        );
    }
    
    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
    
    /**
     * Intent to call a specific tool.
     */
    private record ToolCallIntent(String toolName, Map<String, Object> args) {}
    
    /**
     * Normalized result from a tool call.
     */
    public record NormalizedToolResult(
        String toolName,
        boolean success,
        String data,
        String error,
        long latencyMs
    ) {}
}
