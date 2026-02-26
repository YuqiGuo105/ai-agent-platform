package com.mrpot.agent.service.pipeline;

import com.mrpot.agent.common.replay.ReplayMode;
import com.mrpot.agent.service.ConversationHistoryService;
import com.mrpot.agent.service.KbRetrievalService;
import com.mrpot.agent.service.LlmService;
import com.mrpot.agent.service.RagAnswerService;
import com.mrpot.agent.service.pipeline.stages.ConversationSaveStage;
import com.mrpot.agent.service.pipeline.stages.FileExtractStage;
import com.mrpot.agent.service.pipeline.stages.HistoryStage;
import com.mrpot.agent.service.pipeline.stages.LlmStreamStage;
import com.mrpot.agent.service.pipeline.stages.RagRetrieveStage;
import com.mrpot.agent.service.pipeline.stages.TelemetryStage;
import com.mrpot.agent.service.telemetry.RunLogPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * FAST execution pipeline factory component.
 * 
 * Configures optimal stage ordering and conditions for quick responses.
 * 
 * Pipeline stage execution order:
 * 1. telemetry_start - Publish run.start telemetry event
 * 2. history_retrieve - Retrieve conversation history from Redis
 * 3. file_extract - Extract file content (conditional)
 * 4. rag_retrieve - Search knowledge base (conditional)
 * 5. llm_stream - Stream LLM response
 * 6. conversation_save - Save conversation to Redis
 * 7. telemetry_final - Publish run.final telemetry event
 */
@Slf4j
@Component
public class FastPipeline {
    
    private final RagAnswerService ragAnswerService;
    private final KbRetrievalService kbRetrievalService;
    private final LlmService llmService;
    private final ConversationHistoryService conversationHistoryService;
    private final RunLogPublisher runLogPublisher;
    
    public FastPipeline(
        RagAnswerService ragAnswerService, 
        KbRetrievalService kbRetrievalService,
        LlmService llmService,
        ConversationHistoryService conversationHistoryService,
        RunLogPublisher runLogPublisher
    ) {
        this.ragAnswerService = ragAnswerService;
        this.kbRetrievalService = kbRetrievalService;
        this.llmService = llmService;
        this.conversationHistoryService = conversationHistoryService;
        this.runLogPublisher = runLogPublisher;
    }
    
    /**
     * Build configured FAST pipeline runner.
     * 
     * Pipeline stage execution order:
     * 1. telemetry_start - Publish run.start telemetry event (silent)
     * 2. history_retrieve - Retrieve conversation history from Redis (condition: always execute)
     * 3. file_extract - Extract file content (condition: policy allows and has file URLs)
     * 4. rag_retrieve - Search knowledge base (condition: policy allows RAG)
     * 5. llm_stream - Stream LLM response (always execute)
     * 6. conversation_save - Save conversation to Redis (silent)
     * 7. telemetry_final - Publish run.final telemetry event (silent)
     *
     * @return configured PipelineRunner
     */
    public PipelineRunner build() {
        log.debug("Building FAST pipeline");
        
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
            new HistoryStage(conversationHistoryService, runLogPublisher),
            StageConfig.of(Duration.ofSeconds(5))  // Quick timeout for Redis
        );
        
        // Stage 2: File extraction
        // Condition: policy allows file access AND request has file URLs
        // In LLM_ONLY replay mode, skip file extraction (tool stage)
        runner.addStage(
            "file_extract",
            new FileExtractStage(ragAnswerService),
            StageConfig.of(
                Duration.ofSeconds(30),  
                ctx -> ctx.getReplayMode() != ReplayMode.LLM_ONLY &&
                       ctx.policy().allowFile() && 
                       !ctx.request().resolveFileUrls(3).isEmpty()
            )
        );
        
        // Stage 3: RAG retrieval
        // Condition: policy allows RAG access
        runner.addStage(
            "rag_retrieve",
            new RagRetrieveStage(kbRetrievalService),
            StageConfig.conditional(ctx -> ctx.policy().allowRag())
        );
        
        // Stage 4: LLM streaming response
        // Always execute - this is the core response generation
        runner.addStage(
            "llm_stream",
            new LlmStreamStage(ragAnswerService, llmService),
            StageConfig.of(Duration.ofSeconds(30))
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
        
        // Set global timeout
        runner.withGlobalTimeout(Duration.ofSeconds(60));
        
        return runner;
    }
}
