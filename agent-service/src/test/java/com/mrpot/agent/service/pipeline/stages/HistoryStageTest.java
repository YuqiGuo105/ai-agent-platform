package com.mrpot.agent.service.pipeline.stages;

import com.mrpot.agent.common.api.RagAnswerRequest;
import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.policy.ExecutionPolicy;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.service.ConversationHistoryService;
import com.mrpot.agent.model.ChatMessage;
import com.mrpot.agent.service.pipeline.PipelineContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HistoryStageTest {

    private ConversationHistoryService conversationHistoryService;
    private HistoryStage historyStage;
    private PipelineContext context;

    @BeforeEach
    void setUp() {
        conversationHistoryService = Mockito.mock(ConversationHistoryService.class);
        historyStage = new HistoryStage(conversationHistoryService);

        RagAnswerRequest request = Mockito.mock(RagAnswerRequest.class);
        ExecutionPolicy policy = Mockito.mock(ExecutionPolicy.class);
        
        context = new PipelineContext(
            "run123",
            "trace456",
            "session789",
            "user001",
            request,
            ScopeMode.GENERAL,
            policy,
            "FAST"
        );
    }

    @Test
    void process_retrievesHistoryAndStoresInContext() {
        List<ChatMessage> history = List.of(
            new ChatMessage("user", "question", Instant.parse("2026-01-01T10:00:00Z")),
            new ChatMessage("assistant", "answer", Instant.parse("2026-01-01T10:01:00Z"))
        );
        when(conversationHistoryService.getRecentHistory(eq("session789"), eq(3)))
            .thenReturn(Mono.just(history));

        StepVerifier.create(historyStage.process(null, context))
            .assertNext(envelope -> {
                assertEquals(StageNames.REDIS, envelope.stage());
                assertEquals("question", envelope.message());
                
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) envelope.payload();
                assertEquals(2, payload.get("historyCount"));
                
                @SuppressWarnings("unchecked")
                List<String> recentQuestions = (List<String>) payload.get("recentQuestions");
                assertEquals(1, recentQuestions.size());
                assertEquals("question", recentQuestions.get(0));
                assertEquals("session789", payload.get("sessionId"));
            })
            .verifyComplete();

        // Verify history was stored in context
        List<ChatMessage> storedHistory = context.get(HistoryStage.KEY_CONVERSATION_HISTORY);
        assertNotNull(storedHistory);
        assertEquals(2, storedHistory.size());
    }

    @Test
    void process_handlesEmptyHistory() {
        when(conversationHistoryService.getRecentHistory(anyString(), anyInt()))
            .thenReturn(Mono.just(Collections.emptyList()));

        StepVerifier.create(historyStage.process(null, context))
            .assertNext(envelope -> {
                assertEquals(StageNames.REDIS, envelope.stage());
                
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) envelope.payload();
                assertEquals(0, payload.get("historyCount"));
            })
            .verifyComplete();

        List<ChatMessage> storedHistory = context.get(HistoryStage.KEY_CONVERSATION_HISTORY);
        assertNotNull(storedHistory);
        assertTrue(storedHistory.isEmpty());
    }

    @Test
    void process_handlesErrorGracefully() {
        when(conversationHistoryService.getRecentHistory(anyString(), anyInt()))
            .thenReturn(Mono.error(new RuntimeException("Redis connection failed")));

        StepVerifier.create(historyStage.process(null, context))
            .assertNext(envelope -> {
                assertEquals(StageNames.REDIS, envelope.stage());
                assertEquals("No history", envelope.message());
                
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) envelope.payload();
                assertEquals(0, payload.get("historyCount"));
                assertNotNull(payload.get("error"));
            })
            .verifyComplete();

        // Verify empty history was stored on error
        List<ChatMessage> storedHistory = context.get(HistoryStage.KEY_CONVERSATION_HISTORY);
        assertNotNull(storedHistory);
        assertTrue(storedHistory.isEmpty());
    }

    @Test
    void process_extractsRecentUserQuestions() {
        List<ChatMessage> history = List.of(
            new ChatMessage("user", "What is NASA?", Instant.parse("2026-01-01T12:00:00Z")),
            new ChatMessage("assistant", "NASA is...", Instant.parse("2026-01-01T12:01:00Z")),
            new ChatMessage("user", "Tell me about SpaceX", Instant.parse("2026-01-01T12:02:00Z"))
        );
        when(conversationHistoryService.getRecentHistory(anyString(), anyInt()))
            .thenReturn(Mono.just(history));

        StepVerifier.create(historyStage.process(null, context))
            .assertNext(envelope -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) envelope.payload();
                @SuppressWarnings("unchecked")
                List<String> recentQuestions = (List<String>) payload.get("recentQuestions");
                assertEquals(2, recentQuestions.size());
                assertEquals("What is NASA?", recentQuestions.get(0));
                assertEquals("Tell me about SpaceX", recentQuestions.get(1));
            })
            .verifyComplete();
    }

    @Test
    void process_incrementsSequenceNumber() {
        when(conversationHistoryService.getRecentHistory(anyString(), anyInt()))
            .thenReturn(Mono.just(Collections.emptyList()));

        long initialSeq = context.sseSeq().get();
        
        historyStage.process(null, context).block();

        assertTrue(context.sseSeq().get() > initialSeq);
    }
}
