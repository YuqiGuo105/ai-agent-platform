package com.mrpot.agent.service;

import com.mrpot.agent.common.deep.ToolCallRecord;
import com.mrpot.agent.service.pipeline.PipelineContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for tracking and auditing deep tool calls.
 * Provides round-level metrics including toolCount, successRate, and p95Latency.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeepToolAuditService {
    
    /**
     * Audit data for a single round of tool calls.
     */
    public record RoundAudit(
        int round,
        int toolCount,
        int successCount,
        int failureCount,
        double successRate,
        double p95Latency,
        double avgLatency,
        long totalLatencyMs,
        List<String> toolNames,
        List<String> errors
    ) {
        public static RoundAudit empty(int round) {
            return new RoundAudit(round, 0, 0, 0, 0.0, 0.0, 0.0, 0L, List.of(), List.of());
        }
    }
    
    /**
     * Aggregate audit data across all rounds.
     */
    public record AggregateAudit(
        int totalToolCount,
        int totalSuccessCount,
        int totalFailureCount,
        double overallSuccessRate,
        double p95Latency,
        double avgLatency,
        int roundCount,
        Map<String, Integer> toolUsageCounts,
        List<RoundAudit> roundAudits
    ) {
        public static AggregateAudit empty() {
            return new AggregateAudit(0, 0, 0, 0.0, 0.0, 0.0, 0, Map.of(), List.of());
        }
    }
    
    /**
     * Track a tool call and update audit data in context.
     */
    public void trackToolCall(PipelineContext context, ToolCallRecord record, int round) {
        // Get or create audit map
        @SuppressWarnings("unchecked")
        Map<String, Object> auditMap = context.getOrDefault(PipelineContext.KEY_TOOL_AUDIT, new ConcurrentHashMap<>());
        
        // Track by round
        @SuppressWarnings("unchecked")
        List<ToolCallRecord> roundCalls = (List<ToolCallRecord>) auditMap.computeIfAbsent(
            "round_" + round, 
            k -> Collections.synchronizedList(new ArrayList<>())
        );
        roundCalls.add(record);
        
        // Update total counts
        auditMap.merge("totalCalls", 1, (old, inc) -> ((Integer) old) + (Integer) inc);
        if (record.success()) {
            auditMap.merge("successCalls", 1, (old, inc) -> ((Integer) old) + (Integer) inc);
        } else {
            auditMap.merge("failureCalls", 1, (old, inc) -> ((Integer) old) + (Integer) inc);
        }
        
        context.put(PipelineContext.KEY_TOOL_AUDIT, auditMap);
        
        log.debug("Tracked tool call for runId={}: tool={}, success={}, latency={}ms, round={}",
            context.runId(), record.toolName(), record.success(), record.latencyMs(), round);
    }
    
    /**
     * Record an error for telemetry and reflection/synthesis stages.
     */
    public void recordError(PipelineContext context, String toolName, String error, int round) {
        @SuppressWarnings("unchecked")
        Map<String, Object> auditMap = context.getOrDefault(PipelineContext.KEY_TOOL_AUDIT, new ConcurrentHashMap<>());
        
        @SuppressWarnings("unchecked")
        List<Map<String, String>> errors = (List<Map<String, String>>) auditMap.computeIfAbsent(
            "errors",
            k -> Collections.synchronizedList(new ArrayList<>())
        );
        
        errors.add(Map.of(
            "toolName", toolName,
            "error", error,
            "round", String.valueOf(round),
            "timestamp", String.valueOf(System.currentTimeMillis())
        ));
        
        context.put(PipelineContext.KEY_TOOL_AUDIT, auditMap);
        
        log.warn("Tool error recorded for runId={}: tool={}, error={}, round={}",
            context.runId(), toolName, error, round);
    }
    
    /**
     * Build round-level audit from context.
     */
    public RoundAudit buildRoundAudit(PipelineContext context, int round) {
        @SuppressWarnings("unchecked")
        Map<String, Object> auditMap = context.get(PipelineContext.KEY_TOOL_AUDIT);
        if (auditMap == null) {
            return RoundAudit.empty(round);
        }
        
        @SuppressWarnings("unchecked")
        List<ToolCallRecord> roundCalls = (List<ToolCallRecord>) auditMap.get("round_" + round);
        if (roundCalls == null || roundCalls.isEmpty()) {
            return RoundAudit.empty(round);
        }
        
        return buildAuditFromRecords(roundCalls, round);
    }
    
    /**
     * Build aggregate audit across all rounds from context.
     */
    public AggregateAudit buildAggregateAudit(PipelineContext context) {
        @SuppressWarnings("unchecked")
        Map<String, Object> auditMap = context.get(PipelineContext.KEY_TOOL_AUDIT);
        if (auditMap == null) {
            return AggregateAudit.empty();
        }
        
        List<RoundAudit> roundAudits = new ArrayList<>();
        List<ToolCallRecord> allRecords = new ArrayList<>();
        Map<String, Integer> toolUsageCounts = new HashMap<>();
        
        // Collect all round data
        for (Map.Entry<String, Object> entry : auditMap.entrySet()) {
            if (entry.getKey().startsWith("round_")) {
                int round = Integer.parseInt(entry.getKey().substring(6));
                @SuppressWarnings("unchecked")
                List<ToolCallRecord> roundCalls = (List<ToolCallRecord>) entry.getValue();
                if (roundCalls != null && !roundCalls.isEmpty()) {
                    allRecords.addAll(roundCalls);
                    roundAudits.add(buildAuditFromRecords(roundCalls, round));
                    
                    // Count tool usage
                    for (ToolCallRecord record : roundCalls) {
                        toolUsageCounts.merge(record.toolName(), 1, Integer::sum);
                    }
                }
            }
        }
        
        if (allRecords.isEmpty()) {
            return AggregateAudit.empty();
        }
        
        // Calculate aggregate metrics
        int totalCount = allRecords.size();
        int successCount = (int) allRecords.stream().filter(ToolCallRecord::success).count();
        int failureCount = totalCount - successCount;
        double successRate = totalCount > 0 ? (double) successCount / totalCount : 0.0;
        
        List<Long> latencies = allRecords.stream()
            .map(ToolCallRecord::latencyMs)
            .sorted()
            .toList();
        
        double p95 = calculateP95(latencies);
        double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        
        roundAudits.sort(Comparator.comparingInt(RoundAudit::round));
        
        return new AggregateAudit(
            totalCount,
            successCount,
            failureCount,
            successRate,
            p95,
            avg,
            roundAudits.size(),
            toolUsageCounts,
            roundAudits
        );
    }
    
    private RoundAudit buildAuditFromRecords(List<ToolCallRecord> records, int round) {
        if (records.isEmpty()) {
            return RoundAudit.empty(round);
        }
        
        int total = records.size();
        int success = (int) records.stream().filter(ToolCallRecord::success).count();
        int failure = total - success;
        double successRate = total > 0 ? (double) success / total : 0.0;
        
        List<Long> latencies = records.stream()
            .map(ToolCallRecord::latencyMs)
            .sorted()
            .toList();
        
        double p95 = calculateP95(latencies);
        double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long totalLatency = latencies.stream().mapToLong(Long::longValue).sum();
        
        List<String> toolNames = records.stream()
            .map(ToolCallRecord::toolName)
            .distinct()
            .toList();
        
        List<String> errors = records.stream()
            .filter(r -> !r.success())
            .map(ToolCallRecord::result)
            .toList();
        
        return new RoundAudit(
            round, total, success, failure, successRate,
            p95, avg, totalLatency, toolNames, errors
        );
    }
    
    private double calculateP95(List<Long> sortedLatencies) {
        if (sortedLatencies.isEmpty()) return 0.0;
        int index = (int) Math.ceil(0.95 * sortedLatencies.size()) - 1;
        index = Math.max(0, Math.min(index, sortedLatencies.size() - 1));
        return sortedLatencies.get(index);
    }
}
