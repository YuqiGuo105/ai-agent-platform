package com.mrpot.agent.common.telemetry;

import java.util.List;

/**
 * DTO for representing detailed run information including tool calls.
 * Used by the trace query API to return complete run details.
 */
public record RunDetailDto(
    String runId,
    String traceId,
    String sessionId,
    String mode,
    String model,
    String status,
    Long totalLatencyMs,
    Integer kbHitCount,
    String kbDocIds,              // KB document IDs (comma-separated)
    Long kbLatencyMs,             // KB retrieval latency
    Integer historyCount,         // Number of conversation history messages
    String recentQuestionsJson,   // JSON array of recent user questions
    String question,
    String answerFinal,           // Final answer text
    Double complexityScore,       // Question complexity score
    String executionMode,         // "FAST" or "DEEP"
    Integer deepRoundsUsed,       // Number of deep reasoning rounds
    Integer toolCallsCount,       // Total tool calls count
    Double toolSuccessRate,       // Tool call success rate (0.0 to 1.0)
    String featureBreakdownJson,  // JSON breakdown of features used
    List<ToolCallDto> tools
) {}
