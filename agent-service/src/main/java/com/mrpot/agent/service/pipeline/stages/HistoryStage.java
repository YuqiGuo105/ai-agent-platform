package com.mrpot.agent.service.pipeline.stages;

import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.service.ConversationHistoryService;
import com.mrpot.agent.service.model.ChatMessage;
import com.mrpot.agent.service.pipeline.PipelineContext;
import com.mrpot.agent.service.pipeline.Processor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Pipeline stage for retrieving conversation history from Redis.
 * Fetches recent chat messages and stores them in PipelineContext for use by LLM.
 */
@Slf4j
@RequiredArgsConstructor
public class HistoryStage implements Processor<Void, SseEnvelope> {

    public static final String KEY_CONVERSATION_HISTORY = "conversationHistory";
    private static final int DEFAULT_HISTORY_LIMIT = 3;

    private final ConversationHistoryService conversationHistoryService;

    @Override
    public Mono<SseEnvelope> process(Void input, PipelineContext context) {
        String sessionId = context.sessionId();

        log.debug("Retrieving conversation history for sessionId={}, runId={}",
            sessionId, context.runId());

        return conversationHistoryService.getRecentHistory(sessionId, DEFAULT_HISTORY_LIMIT)
            .map(history -> {
                // Store history in pipeline context
                context.put(KEY_CONVERSATION_HISTORY, history);

                // Calculate metadata
                int historyCount = history.size();
                Instant oldestTimestamp = findOldestTimestamp(history);

                log.info("Retrieved {} messages from conversation history for runId={}",
                    historyCount, context.runId());

                // Create SSE envelope with history retrieval result
                return new SseEnvelope(
                    StageNames.REDIS,
                    "Conversation history retrieved",
                    Map.of(
                        "historyCount", historyCount,
                        "oldestMessageTimestamp", oldestTimestamp != null 
                            ? oldestTimestamp.toString() 
                            : null,
                        "sessionId", sessionId
                    ),
                    context.nextSeq(),
                    System.currentTimeMillis(),
                    context.traceId(),
                    context.sessionId()
                );
            })
            .onErrorResume(e -> {
                log.error("Failed to retrieve conversation history for runId={}: {}",
                    context.runId(), e.getMessage(), e);

                // Store empty history on error - don't break the pipeline
                context.put(KEY_CONVERSATION_HISTORY, Collections.emptyList());

                // Return a non-blocking error indicator
                return Mono.just(new SseEnvelope(
                    StageNames.REDIS,
                    "Conversation history retrieval failed (continuing without history)",
                    Map.of(
                        "historyCount", 0,
                        "error", e.getMessage() != null ? e.getMessage() : "Unknown error",
                        "sessionId", sessionId
                    ),
                    context.nextSeq(),
                    System.currentTimeMillis(),
                    context.traceId(),
                    context.sessionId()
                ));
            });
    }

    /**
     * Find the oldest timestamp in the conversation history.
     *
     * @param history list of chat messages
     * @return the oldest timestamp, or null if history is empty
     */
    private Instant findOldestTimestamp(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }

        return history.stream()
            .map(ChatMessage::timestamp)
            .filter(t -> t != null)
            .min(Instant::compareTo)
            .orElse(null);
    }
}
