package com.mrpot.agent.service.pipeline.stages;

import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.service.ConversationHistoryService;
import com.mrpot.agent.service.pipeline.PipelineContext;
import com.mrpot.agent.service.pipeline.Processor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Conversation save stage - saves user question and assistant answer to Redis.
 * 
 * This stage executes after LLM streaming response completes, saving conversation history to Redis,
 * so that subsequent requests can retrieve conversation context.
 * 
 * Features:
 * - Save failures do not cause pipeline failure (gracefully handled with onErrorResume)
 * - Does not emit SSE events (silent execution)
 * - Retrieves question and answer from PipelineContext
 */
@Slf4j
@RequiredArgsConstructor
public class ConversationSaveStage implements Processor<Void, SseEnvelope> {
    
    private final ConversationHistoryService conversationHistoryService;
    
    @Override
    public Mono<SseEnvelope> process(Void input, PipelineContext context) {
        String sessionId = context.sessionId();
        String question = context.request().question();
        String answer = context.getFinalAnswer();
        
        log.debug("Saving conversation: sessionId={}, questionLength={}, answerLength={}, runId={}",
            sessionId, 
            question != null ? question.length() : 0,
            answer != null ? answer.length() : 0,
            context.runId());
        
        // If no answer content, skip save
        if (answer == null || answer.isBlank()) {
            log.warn("Skipping conversation save: empty answer for runId={}", context.runId());
            return Mono.empty();
        }
        
        return conversationHistoryService.saveConversationPair(sessionId, question, answer)
            .then(Mono.fromSupplier(() -> {
                log.info("Conversation saved successfully: sessionId={}, runId={}", 
                    sessionId, context.runId());
                
                // Return a save complete event (optional, won't be sent when configured as silent)
                return new SseEnvelope(
                    StageNames.REDIS,
                    "History saved",
                    Map.of(
                        "sessionId", sessionId,
                        "questionLength", question != null ? question.length() : 0,
                        "answerLength", answer.length()
                    ),
                    context.nextSeq(),
                    System.currentTimeMillis(),
                    context.traceId(),
                    context.sessionId()
                );
            }))
            .onErrorResume(e -> {
                // Save failure should not cause pipeline failure
                log.error("Failed to save conversation for runId={}: {}", 
                    context.runId(), e.getMessage(), e);
                return Mono.empty();
            });
    }
}
