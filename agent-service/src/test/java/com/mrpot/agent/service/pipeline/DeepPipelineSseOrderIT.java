package com.mrpot.agent.service.pipeline;

import com.mrpot.agent.common.api.RagAnswerRequest;
import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.policy.ExecutionPolicy;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.service.ConversationHistoryService;
import com.mrpot.agent.service.telemetry.RunLogPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for DeepPipeline SSE event ordering.
 * Verifies that SSE events are emitted in the correct sequence:
 * START → REDIS (history_retrieve) → DEEP_PLAN → DEEP_REASONING → DEEP_SYNTHESIS → ANSWER_FINAL
 */
@DisplayName("DeepPipeline SSE Order Integration Tests")
class DeepPipelineSseOrderIT {

    private ConversationHistoryService conversationHistoryService;
    private RunLogPublisher runLogPublisher;
    private DeepPipeline deepPipeline;
    private PipelineContext context;

    @BeforeEach
    void setUp() {
        conversationHistoryService = Mockito.mock(ConversationHistoryService.class);
        runLogPublisher = Mockito.mock(RunLogPublisher.class);
        
        deepPipeline = new DeepPipeline(conversationHistoryService, runLogPublisher);
        
        // Mock conversation history service behavior
        when(conversationHistoryService.getRecentHistory(anyString(), anyInt()))
            .thenReturn(Mono.just(Collections.emptyList()));
        when(conversationHistoryService.saveConversationPair(anyString(), anyString(), anyString()))
            .thenReturn(Mono.empty());
        
        // Mock run log publisher behavior (void method, just verify it doesn't throw)
        doNothing().when(runLogPublisher).publish(any());
        
        // Create test context
        context = createTestContext();
    }

    @Test
    @DisplayName("DEEP pipeline should emit SSE events in correct order")
    void deepPipeline_emitsSseEventsInCorrectOrder() {
        // Arrange
        PipelineRunner runner = deepPipeline.build();
        List<String> stageNames = new ArrayList<>();
        
        // Act & Assert
        StepVerifier.create(runner.run(context))
            .thenConsumeWhile(envelope -> {
                stageNames.add(envelope.stage());
                return true;
            })
            .verifyComplete();
        
        // Verify correct order of non-silent stages
        assertFalse(stageNames.isEmpty(), "Should have emitted SSE events");
        
        // Verify START is first
        assertEquals(StageNames.START, stageNames.get(0), 
            "First event should be START");
        
        // Verify ANSWER_FINAL is last
        assertEquals(StageNames.ANSWER_FINAL, stageNames.get(stageNames.size() - 1), 
            "Last event should be ANSWER_FINAL");
        
        // Verify DEEP stages are present in order
        int redisIndex = stageNames.indexOf(StageNames.REDIS);
        int deepPlanIndex = stageNames.indexOf(StageNames.DEEP_PLAN);
        int deepReasoningIndex = stageNames.indexOf(StageNames.DEEP_REASONING);
        int deepSynthesisIndex = stageNames.indexOf(StageNames.DEEP_SYNTHESIS);
        
        assertTrue(redisIndex > 0, "REDIS stage should be present");
        assertTrue(deepPlanIndex > redisIndex, "DEEP_PLAN should come after REDIS");
        assertTrue(deepReasoningIndex > deepPlanIndex, "DEEP_REASONING should come after DEEP_PLAN");
        assertTrue(deepSynthesisIndex > deepReasoningIndex, "DEEP_SYNTHESIS should come after DEEP_REASONING");
    }

    @Test
    @DisplayName("DEEP pipeline should include all expected stage names")
    void deepPipeline_includesAllExpectedStageNames() {
        // Arrange
        PipelineRunner runner = deepPipeline.build();
        List<String> stageNames = new ArrayList<>();
        
        // Act
        runner.run(context)
            .toStream()
            .forEach(envelope -> stageNames.add(envelope.stage()));
        
        // Assert
        assertTrue(stageNames.contains(StageNames.START), "Should contain START");
        assertTrue(stageNames.contains(StageNames.REDIS), "Should contain REDIS (history)");
        assertTrue(stageNames.contains(StageNames.DEEP_PLAN), "Should contain DEEP_PLAN");
        assertTrue(stageNames.contains(StageNames.DEEP_REASONING), "Should contain DEEP_REASONING");
        assertTrue(stageNames.contains(StageNames.DEEP_SYNTHESIS), "Should contain DEEP_SYNTHESIS");
        assertTrue(stageNames.contains(StageNames.ANSWER_FINAL), "Should contain ANSWER_FINAL");
    }

    @Test
    @DisplayName("DEEP pipeline should set final answer in context")
    void deepPipeline_setsFinalAnswerInContext() {
        // Arrange
        PipelineRunner runner = deepPipeline.build();
        
        // Act
        runner.run(context).blockLast();
        
        // Assert
        String finalAnswer = context.getFinalAnswer();
        assertNotNull(finalAnswer, "Final answer should be set");
        assertFalse(finalAnswer.isEmpty(), "Final answer should not be empty");
        assertEquals("DEEP mode answer", finalAnswer, "Final answer should match expected value");
    }

    @Test
    @DisplayName("DEEP pipeline should set deep plan in context")
    void deepPipeline_setsDeepPlanInContext() {
        // Arrange
        PipelineRunner runner = deepPipeline.build();
        
        // Act
        runner.run(context).blockLast();
        
        // Assert
        var deepPlan = context.getDeepPlan();
        assertNotNull(deepPlan, "Deep plan should be set");
        assertEquals("created", deepPlan.get("status"), "Deep plan status should be 'created'");
    }

    @Test
    @DisplayName("DEEP pipeline should set deep reasoning in context")
    void deepPipeline_setsDeepReasoningInContext() {
        // Arrange
        PipelineRunner runner = deepPipeline.build();
        
        // Act
        runner.run(context).blockLast();
        
        // Assert
        var deepReasoning = context.getDeepReasoning();
        assertNotNull(deepReasoning, "Deep reasoning should be set");
        assertEquals("complete", deepReasoning.get("status"), "Deep reasoning status should be 'complete'");
    }

    @Test
    @DisplayName("DEEP pipeline should set deep synthesis in context")
    void deepPipeline_setsDeepSynthesisInContext() {
        // Arrange
        PipelineRunner runner = deepPipeline.build();
        
        // Act
        runner.run(context).blockLast();
        
        // Assert
        var deepSynthesis = context.getDeepSynthesis();
        assertNotNull(deepSynthesis, "Deep synthesis should be set");
        assertEquals("complete", deepSynthesis.get("status"), "Deep synthesis status should be 'complete'");
    }

    @Test
    @DisplayName("DEEP pipeline SSE envelopes should have incrementing sequence numbers")
    void deepPipeline_sseEnvelopesHaveIncrementingSequence() {
        // Arrange
        PipelineRunner runner = deepPipeline.build();
        List<Long> sequences = new ArrayList<>();
        
        // Act
        runner.run(context)
            .toStream()
            .forEach(envelope -> sequences.add(envelope.seq()));
        
        // Assert
        assertFalse(sequences.isEmpty(), "Should have emitted SSE events");
        
        // Verify sequences are monotonically increasing
        for (int i = 1; i < sequences.size(); i++) {
            assertTrue(sequences.get(i) > sequences.get(i - 1),
                "Sequence numbers should be increasing: " + sequences.get(i) + " > " + sequences.get(i - 1));
        }
    }

    @Test
    @DisplayName("DEEP pipeline SSE envelopes should have valid timestamps")
    void deepPipeline_sseEnvelopesHaveValidTimestamps() {
        // Arrange
        PipelineRunner runner = deepPipeline.build();
        long startTime = System.currentTimeMillis();
        
        // Act
        runner.run(context)
            .toStream()
            .forEach(envelope -> {
                // Assert each envelope has a valid timestamp
                assertTrue(envelope.ts() >= startTime,
                    "Timestamp should be >= start time");
                assertTrue(envelope.ts() <= System.currentTimeMillis(),
                    "Timestamp should be <= current time");
            });
    }

    // Helper methods

    private PipelineContext createTestContext() {
        RagAnswerRequest request = Mockito.mock(RagAnswerRequest.class);
        when(request.question()).thenReturn("test question");
        when(request.sessionId()).thenReturn("test-session");
        
        ExecutionPolicy policy = Mockito.mock(ExecutionPolicy.class);
        when(policy.allowRag()).thenReturn(false);
        when(policy.allowFile()).thenReturn(false);
        
        return PipelineContext.builder()
            .runId("test-run-123")
            .traceId("test-trace-456")
            .sessionId("test-session")
            .userId("test-user")
            .request(request)
            .scopeMode(ScopeMode.GENERAL)
            .policy(policy)
            .executionMode("DEEP")
            .build();
    }
}
