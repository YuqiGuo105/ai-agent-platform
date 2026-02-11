package com.mrpot.agent.service.pipeline.stages;

import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.service.ConversationHistoryService;
import com.mrpot.agent.model.ChatMessage;
import com.mrpot.agent.service.pipeline.PipelineContext;
import com.mrpot.agent.service.pipeline.Processor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

                // Extract recent user questions for frontend display
                List<String> recentQuestions = history.stream()
                    .filter(msg -> "user".equalsIgnoreCase(msg.role()))
                    .map(ChatMessage::content)
                    .collect(Collectors.toList());

                log.info("Retrieved {} messages from conversation history for runId={}",
                    historyCount, context.runId());

                // Build display message from recent questions
                String displayMessage = recentQuestions.isEmpty()
                    ? "No history"
                    : String.join(", ", recentQuestions);

                // Create SSE envelope with history retrieval result
                return new SseEnvelope(
                    StageNames.REDIS,
                    displayMessage,
                    Map.of(
                        "historyCount", historyCount,
                        "recentQuestions", recentQuestions,
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
                    "No history",
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
}
