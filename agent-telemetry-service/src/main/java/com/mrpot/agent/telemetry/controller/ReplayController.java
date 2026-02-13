package com.mrpot.agent.telemetry.controller;

import com.mrpot.agent.common.replay.ReplayCommand;
import com.mrpot.agent.telemetry.config.CommandAmqpConfig;
import com.mrpot.agent.telemetry.dto.ReplayRequest;
import com.mrpot.agent.telemetry.dto.ReplayResponse;
import com.mrpot.agent.telemetry.entity.KnowledgeRunEntity;
import com.mrpot.agent.telemetry.repository.KnowledgeRunJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/runs")
@RequiredArgsConstructor
@Tag(name = "Run Replay", description = "Replay previous AI agent runs")
public class ReplayController {

    private static final Logger log = LoggerFactory.getLogger(ReplayController.class);

    private final KnowledgeRunJpaRepository runRepo;
    private final RabbitTemplate rabbitTemplate;

    @PostMapping("/{runId}/replay")
    @Operation(
        summary = "Replay a run",
        description = "Create a new child run that replays a previous run. Supports FULL, TOOLS_ONLY, and LLM_ONLY replay modes."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Replay initiated successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ReplayResponse.class))
        ),
        @ApiResponse(responseCode = "404", description = "Parent run not found", content = @Content)
    })
    public ResponseEntity<ReplayResponse> replayRun(
            @Parameter(description = "ID of the run to replay", required = true)
            @PathVariable String runId,
            @RequestBody ReplayRequest request) {

        KnowledgeRunEntity parentRun = runRepo.findById(runId).orElse(null);
        if (parentRun == null) {
            log.warn("Replay requested for non-existent run: {}", runId);
            return ResponseEntity.notFound().build();
        }

        String newRunId = UUID.randomUUID().toString();

        ReplayCommand command = new ReplayCommand();
        command.setParentRunId(runId);
        command.setNewRunId(newRunId);
        command.setMode(request.getMode());
        command.setAllowedTools(request.getAllowedTools());
        command.setQuestion(parentRun.getQuestion());
        command.setSessionId(parentRun.getSessionId());
        command.setUserId(parentRun.getUserId());
        command.setModel(parentRun.getModel());

        rabbitTemplate.convertAndSend(
            CommandAmqpConfig.COMMAND_EXCHANGE,
            CommandAmqpConfig.REPLAY_ROUTING_KEY,
            command
        );

        log.info("Replay initiated: parentRunId={}, newRunId={}, mode={}",
            runId, newRunId, request.getMode());

        return ResponseEntity.ok(new ReplayResponse(newRunId));
    }

    @GetMapping("/{runId}/children")
    @Operation(
        summary = "Get child runs",
        description = "Retrieve all child runs (replays) of a given parent run."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Child runs retrieved successfully",
            content = @Content(mediaType = "application/json")
        )
    })
    public ResponseEntity<List<KnowledgeRunEntity>> getChildRuns(
            @Parameter(description = "ID of the parent run", required = true)
            @PathVariable String runId) {
        List<KnowledgeRunEntity> children = runRepo.findByParentRunId(runId);
        return ResponseEntity.ok(children);
    }
}
