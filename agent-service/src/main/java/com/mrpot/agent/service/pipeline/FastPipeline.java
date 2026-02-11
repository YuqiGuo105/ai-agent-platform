package com.mrpot.agent.service.pipeline;

import com.mrpot.agent.service.ConversationHistoryService;
import com.mrpot.agent.service.KbRetrievalService;
import com.mrpot.agent.service.LlmService;
import com.mrpot.agent.service.RagAnswerService;
import com.mrpot.agent.service.pipeline.stages.FileExtractStage;
import com.mrpot.agent.service.pipeline.stages.HistoryStage;
import com.mrpot.agent.service.pipeline.stages.LlmStreamStage;
import com.mrpot.agent.service.pipeline.stages.RagRetrieveStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Factory component for building the FAST execution pipeline.
 * Configures optimal stage ordering and conditions for quick responses.
 */
@Slf4j
@Component
public class FastPipeline {
    
    private final RagAnswerService ragAnswerService;
    private final KbRetrievalService kbRetrievalService;
    private final LlmService llmService;
    private final ConversationHistoryService conversationHistoryService;
    
    public FastPipeline(
        RagAnswerService ragAnswerService, 
        KbRetrievalService kbRetrievalService,
        LlmService llmService,
        ConversationHistoryService conversationHistoryService
    ) {
        this.ragAnswerService = ragAnswerService;
        this.kbRetrievalService = kbRetrievalService;
        this.llmService = llmService;
        this.conversationHistoryService = conversationHistoryService;
    }
    
    /**
     * Build a configured FAST pipeline runner.
     * 
     * Pipeline stages:
     * 1. History - Retrieve conversation history from Redis (conditional)
     * 2. FileExtract - Extract content from attached files (conditional)
     * 3. RagRetrieve - Search knowledge base (conditional)
     * 4. LlmStream - Stream LLM response (always)
     *
     * @return configured PipelineRunner
     */
    public PipelineRunner build() {
        log.debug("Building FAST pipeline");
        
        PipelineRunner runner = new PipelineRunner();
        
        // Stage 0: History retrieval from Redis
        // Condition: always execute to get conversation context
        runner.addStage(
            "history_retrieve",
            new HistoryStage(conversationHistoryService),
            StageConfig.of(Duration.ofSeconds(5))  // Quick timeout for Redis
        );
        
        // Stage 1: File extraction
        // Condition: policy allows file access AND request has file URLs
        runner.addStage(
            "file_extract",
            new FileExtractStage(ragAnswerService),
            StageConfig.of(
                Duration.ofSeconds(30),  
                ctx -> ctx.policy().allowFile() && 
                       !ctx.request().resolveFileUrls(3).isEmpty()
            )
        );
        
        // Stage 2: RAG retrieval
        // Condition: policy allows RAG access
        runner.addStage(
            "rag_retrieve",
            new RagRetrieveStage(kbRetrievalService),
            StageConfig.conditional(ctx -> ctx.policy().allowRag())
        );
        
        // Stage 3: LLM streaming
        // Always execute - this is the core response generation
        runner.addStage(
            "llm_stream",
            new LlmStreamStage(ragAnswerService, llmService),
            StageConfig.of(Duration.ofSeconds(30))
        );
        
        // Set global timeout
        runner.withGlobalTimeout(Duration.ofSeconds(60));
        
        return runner;
    }
}
