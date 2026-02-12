package com.mrpot.agent.service.pipeline;

import com.mrpot.agent.service.ConversationHistoryService;
import com.mrpot.agent.service.pipeline.stages.ConversationSaveStage;
import com.mrpot.agent.service.pipeline.stages.DeepPlanStage;
import com.mrpot.agent.service.pipeline.stages.DeepReasoningStage;
import com.mrpot.agent.service.pipeline.stages.DeepSynthesisStage;
import com.mrpot.agent.service.pipeline.stages.HistoryStage;
import com.mrpot.agent.service.pipeline.stages.TelemetryStage;
import com.mrpot.agent.service.telemetry.RunLogPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * DEEP execution pipeline factory component.
 * 
 * Configures deep reasoning pipeline with multi-step processing stages.
 * 
 * Pipeline stage execution order:
 * 1. telemetry_start - Publish run.start telemetry event
 * 2. history_retrieve - Retrieve conversation history from Redis
 * 3. deep_plan - Create reasoning plan
 * 4. deep_reasoning - Execute reasoning steps
 * 5. deep_synthesis - Synthesize final answer
 * 6. conversation_save - Save conversation to Redis
 * 7. telemetry_final - Publish run.final telemetry event
 */
@Slf4j
@Component
public class DeepPipeline {
    
    private final ConversationHistoryService conversationHistoryService;
    private final RunLogPublisher runLogPublisher;
    
    public DeepPipeline(
        ConversationHistoryService conversationHistoryService,
        RunLogPublisher runLogPublisher
    ) {
        this.conversationHistoryService = conversationHistoryService;
        this.runLogPublisher = runLogPublisher;
    }
    
    /**
     * Build configured DEEP pipeline runner.
     * 
     * Pipeline stage execution order:
     * 1. telemetry_start - Publish run.start telemetry event (silent)
     * 2. history_retrieve - Retrieve conversation history from Redis
     * 3. deep_plan - Create reasoning plan
     * 4. deep_reasoning - Execute reasoning steps
     * 5. deep_synthesis - Synthesize final answer
     * 6. conversation_save - Save conversation to Redis (silent)
     * 7. telemetry_final - Publish run.final telemetry event (silent)
     *
     * @return configured PipelineRunner
     */
    public PipelineRunner build() {
        log.debug("Building DEEP pipeline");
        
        PipelineRunner runner = new PipelineRunner();
        
        // Stage 0: Publish run.start telemetry event
        // Silent execution, no SSE events
        runner.addStage(
            "telemetry_start",
            TelemetryStage.start(runLogPublisher),
            StageConfig.silent()
        );
        
        // Stage 1: Retrieve conversation history from Redis
        // Condition: always execute to get conversation context
        runner.addStage(
            "history_retrieve",
            new HistoryStage(conversationHistoryService),
            StageConfig.of(Duration.ofSeconds(5))  // Quick timeout for Redis
        );
        
        // Stage 2: Create reasoning plan
        // Generate plan for deep reasoning process
        runner.addStage(
            "deep_plan",
            new DeepPlanStage(),
            StageConfig.of(Duration.ofSeconds(10))
        );
        
        // Stage 3: Execute reasoning steps
        // Perform reasoning based on the generated plan
        runner.addStage(
            "deep_reasoning",
            new DeepReasoningStage(),
            StageConfig.of(Duration.ofSeconds(15))
        );
        
        // Stage 4: Synthesize final answer
        // Combine reasoning results into coherent response
        runner.addStage(
            "deep_synthesis",
            new DeepSynthesisStage(),
            StageConfig.of(Duration.ofSeconds(10))
        );
        
        // Stage 5: Save conversation to Redis
        // Silent execution, save failures do not affect pipeline
        runner.addStage(
            "conversation_save",
            new ConversationSaveStage(conversationHistoryService),
            StageConfig.silent()
        );
        
        // Stage 6: Publish run.final telemetry event
        // Silent execution, no SSE events
        runner.addStage(
            "telemetry_final",
            TelemetryStage.finalEvent(runLogPublisher),
            StageConfig.silent()
        );
        
        // Set global timeout for DEEP pipeline (longer than FAST due to reasoning steps)
        runner.withGlobalTimeout(Duration.ofSeconds(90));
        
        return runner;
    }
}
