package com.mrpot.agent.telemetry.controller;

import com.mrpot.agent.common.telemetry.RunDetailDto;
import com.mrpot.agent.common.telemetry.ToolCallDto;
import com.mrpot.agent.telemetry.entity.KnowledgeRunEntity;
import com.mrpot.agent.telemetry.entity.KnowledgeToolCallEntity;
import com.mrpot.agent.telemetry.repository.KnowledgeRunEventJpaRepository;
import com.mrpot.agent.telemetry.repository.KnowledgeRunJpaRepository;
import com.mrpot.agent.telemetry.repository.KnowledgeToolCallJpaRepository;
import com.mrpot.agent.telemetry.service.KbServiceClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for querying run and tool call traces.
 * Provides endpoints for full observability of telemetry data.
 */
@RestController
@RequestMapping("/api/runs")
@RequiredArgsConstructor
@Tag(name = "Trace Query", description = "Query run and tool call traces")
public class TraceQueryController {

    private static final Logger log = LoggerFactory.getLogger(TraceQueryController.class);

    private final KnowledgeRunJpaRepository runRepo;
    private final KnowledgeToolCallJpaRepository toolCallRepo;
    private final KnowledgeRunEventJpaRepository eventRepo;
    private final KbServiceClient kbServiceClient;

    /**
     * Get detailed run information including all tool calls.
     *
     * @param runId the run ID to query
     * @return run details with tool calls, or 404 if not found
     */
    @GetMapping("/{runId}")
    @Operation(
        summary = "Get run details",
        description = "Retrieves complete run information including metadata, status, latency metrics, and all associated tool calls ordered by execution time."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Run found and returned successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RunDetailDto.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Run not found with the specified ID",
            content = @Content
        )
    })
    public ResponseEntity<RunDetailDto> getRunDetail(
            @Parameter(description = "Unique run identifier (UUID)", example = "550e8400-e29b-41d4-a716-446655440000", required = true)
            @PathVariable String runId) {
        log.info("Getting run details for runId: {}", runId);
        
        KnowledgeRunEntity run = runRepo.findById(runId).orElse(null);
        if (run == null) {
            log.debug("Run not found: {}", runId);
            return ResponseEntity.notFound().build();
        }
        
        // Debug logging for enrichment data
        String questionsPreview = run.getRecentQuestionsJson() != null 
            ? run.getRecentQuestionsJson().substring(0, Math.min(100, run.getRecentQuestionsJson().length())) 
            : "null";
        log.info("Run {} - historyCount: {}, recentQuestionsJson: {}, kbDocIds: {}, kbHitCount: {}",
            runId, 
            run.getHistoryCount(), 
            questionsPreview,
            run.getKbDocIds(),
            run.getKbHitCount());

        List<KnowledgeToolCallEntity> toolCalls = toolCallRepo.findByRunIdOrderByCreatedAt(runId);
        log.info("Run {} - Found {} tool call records in database", runId, toolCalls.size());

        List<ToolCallDto> toolDtos = toolCalls.stream()
                .map(this::toToolCallDto)
                .collect(Collectors.toList());
        
        // Fetch KB context text from KB service (server-side enrichment)
        String kbContextText = kbServiceClient.fetchKbContextText(run.getKbDocIds());

        RunDetailDto dto = new RunDetailDto(
                run.getId(),
                run.getTraceId(),
                run.getSessionId(),
                run.getMode(),
                run.getModel(),
                run.getStatus(),
                run.getTotalLatencyMs(),
                run.getKbHitCount(),
                run.getKbDocIds(),              // KB document IDs
                kbContextText,                  // KB context (fetched from KB service)
                run.getKbLatencyMs(),           // KB latency
                run.getHistoryCount(),          // History count
                run.getRecentQuestionsJson(),   // Recent questions JSON
                run.getQuestion(),
                run.getAnswerFinal(),           // Final answer
                run.getComplexityScore(),       // Complexity score
                run.getExecutionMode(),         // Execution mode
                run.getDeepRoundsUsed(),        // Deep rounds used
                run.getToolCallsCount(),        // Tool calls count
                run.getToolSuccessRate(),       // Tool success rate
                run.getFeatureBreakdownJson(),  // Feature breakdown JSON
                toolDtos,
                run.getCreatedAt(),             // Start timestamp
                run.getUpdatedAt()              // End timestamp
        );

        return ResponseEntity.ok(dto);
    }

    /**
     * Get only tool calls for a specific run.
     *
     * @param runId the run ID to query
     * @return list of tool calls for the run
     */
    @GetMapping("/{runId}/tools")
    @Operation(
        summary = "Get tool calls for run",
        description = "Retrieves only the tool calls associated with a specific run, ordered by creation time. Useful for analyzing tool execution patterns without full run details."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Tool calls retrieved successfully (may be empty list if no tools were called)",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = ToolCallDto.class)))
        )
    })
    public ResponseEntity<List<ToolCallDto>> getToolCalls(
            @Parameter(description = "Unique run identifier (UUID)", example = "550e8400-e29b-41d4-a716-446655440000", required = true)
            @PathVariable String runId) {
        log.debug("Getting tool calls for runId: {}", runId);
        
        List<KnowledgeToolCallEntity> toolCalls = toolCallRepo.findByRunIdOrderByCreatedAt(runId);

        List<ToolCallDto> dtos = toolCalls.stream()
                .map(this::toToolCallDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Search runs with optional filters.
     *
     * @param sessionId optional session ID filter
     * @param status optional status filter (RUNNING/DONE/FAILED/CANCELLED)
     * @return list of matching runs
     */
    @GetMapping
    @Operation(
        summary = "Search runs",
        description = "Search and filter runs by various criteria. Returns all runs if no filters are specified. Supports filtering by session ID and/or status."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Search completed successfully",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = KnowledgeRunEntity.class)))
        )
    })
    public ResponseEntity<List<KnowledgeRunEntity>> searchRuns(
            @Parameter(description = "Filter by session ID (exact match)", example = "session-abc-123")
            @RequestParam(required = false) String sessionId,
            @Parameter(description = "Filter by run status", example = "DONE", schema = @Schema(allowableValues = {"RUNNING", "DONE", "FAILED", "CANCELLED"}))
            @RequestParam(required = false) String status,
            @Parameter(description = "Filter by parent run ID to find replay children", example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestParam(required = false) String parentRunId) {
        log.debug("Searching runs with sessionId={}, status={}, parentRunId={}", sessionId, status, parentRunId);
        
        List<KnowledgeRunEntity> runs;

        if (parentRunId != null && !parentRunId.isBlank()) {
            runs = runRepo.findByParentRunId(parentRunId);
        } else {
            runs = runRepo.findAll();
        }

        if (sessionId != null && !sessionId.isBlank()) {
            runs = runs.stream()
                    .filter(r -> sessionId.equals(r.getSessionId()))
                    .collect(Collectors.toList());
        }
        if (status != null && !status.isBlank()) {
            runs = runs.stream()
                    .filter(r -> status.equals(r.getStatus()))
                    .collect(Collectors.toList());
        }

        return ResponseEntity.ok(runs);
    }

    /**
     * Delete a run and all associated data (tool calls, events).
     *
     * @param runId the run ID to delete
     * @return success message or 404 if not found
     */
    @DeleteMapping("/{runId}")
    @Transactional
    @Operation(
        summary = "Delete run",
        description = "Deletes a run and all associated tool calls and events. This operation is irreversible."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Run deleted successfully"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Run not found"
        )
    })
    public ResponseEntity<Map<String, Object>> deleteRun(
            @Parameter(description = "Unique run identifier (UUID)", required = true)
            @PathVariable String runId) {
        log.info("Deleting run and associated data for runId: {}", runId);
        
        // Check if run exists
        if (!runRepo.existsById(runId)) {
            log.debug("Run not found for deletion: {}", runId);
            return ResponseEntity.notFound().build();
        }
        
        // Delete associated tool calls
        int toolCallsDeleted = toolCallRepo.deleteByRunId(runId);
        log.debug("Deleted {} tool calls for runId: {}", toolCallsDeleted, runId);
        
        // Delete associated events
        int eventsDeleted = eventRepo.deleteByRunId(runId);
        log.debug("Deleted {} events for runId: {}", eventsDeleted, runId);
        
        // Delete the run itself
        runRepo.deleteById(runId);
        log.info("Successfully deleted run {} with {} tool calls and {} events", runId, toolCallsDeleted, eventsDeleted);
        
        return ResponseEntity.ok(Map.of(
            "deleted", true,
            "runId", runId,
            "toolCallsDeleted", toolCallsDeleted,
            "eventsDeleted", eventsDeleted
        ));
    }

    /**
     * Batch delete multiple runs and all associated data.
     *
     * @param runIds list of run IDs to delete
     * @return summary of deleted counts
     */
    @DeleteMapping("/batch")
    @Transactional
    @Operation(
        summary = "Batch delete runs",
        description = "Deletes multiple runs and all their associated tool calls and events. This operation is irreversible."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Runs deleted successfully"
        )
    })
    public ResponseEntity<Map<String, Object>> deleteRunsBatch(
            @RequestBody List<String> runIds) {
        log.info("Batch deleting {} runs", runIds.size());
        
        int totalToolCalls = 0;
        int totalEvents = 0;
        int runsDeleted = 0;
        
        for (String runId : runIds) {
            if (runRepo.existsById(runId)) {
                totalToolCalls += toolCallRepo.deleteByRunId(runId);
                totalEvents += eventRepo.deleteByRunId(runId);
                runRepo.deleteById(runId);
                runsDeleted++;
            }
        }
        
        log.info("Batch deleted {} runs with {} tool calls and {} events", runsDeleted, totalToolCalls, totalEvents);
        
        return ResponseEntity.ok(Map.of(
            "deleted", true,
            "runsDeleted", runsDeleted,
            "toolCallsDeleted", totalToolCalls,
            "eventsDeleted", totalEvents
        ));
    }

    /**
     * Convert entity to DTO.
     */
    private ToolCallDto toToolCallDto(KnowledgeToolCallEntity tc) {
        return new ToolCallDto(
                tc.getId(),
                tc.getToolName(),
                tc.getAttempt(),
                tc.getOk(),
                tc.getDurationMs(),
                tc.getArgsPreview(),
                tc.getResultPreview(),
                tc.getCacheHit(),
                tc.getFreshness(),
                tc.getErrorCode(),
                tc.getErrorMsg(),
                tc.getRetryable(),
                tc.getCalledAt(),  // Use calledAt as sourceTs
                tc.getKeyInfoJson()    // Key info JSON
        );
    }
}
