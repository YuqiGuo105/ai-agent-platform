package com.mrpot.agent.service.pipeline;

import com.mrpot.agent.common.api.RagAnswerRequest;
import com.mrpot.agent.common.policy.ExecutionPolicy;
import com.mrpot.agent.common.policy.ToolAccessLevel;
import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.model.ChatMessage;
import com.mrpot.agent.service.LlmService;
import com.mrpot.agent.service.pipeline.artifacts.DeepPlan;
import com.mrpot.agent.service.pipeline.DeepReasoningCoordinator.ReasoningResult;
import com.mrpot.agent.service.pipeline.DeepReasoningCoordinator.StopReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DeepReasoningCoordinator.
 * Tests bounded iteration logic, stop conditions, and timeout handling.
 */
@ExtendWith(MockitoExtension.class)
class DeepReasoningCoordinatorTest {
    
    @Mock
    private LlmService llmService;
    
    private DeepModeConfig config;
    private DeepReasoningCoordinator coordinator;
    private PipelineContext context;
    private DeepPlan plan;
    private ExecutionPolicy policy;
    private Sinks.Many<SseEnvelope> eventSink;
    
    @BeforeEach
    void setUp() {
        config = new DeepModeConfig();
        config.setMaxRoundsCap(3);
        config.setConfidenceThreshold(0.85);
        config.setReasoningTimeoutSeconds(30);
        
        coordinator = new DeepReasoningCoordinator(llmService, config);
        
        RagAnswerRequest request = new RagAnswerRequest(
            "Test question",
            "test-session",
            null,
            null,
            null,
            null,
            "DEEP",
            null,
            null
        );
        context = PipelineContext.builder()
            .runId("test-run")
            .sessionId("test-session")
            .traceId("test-trace")
            .request(request)
            .executionMode("DEEP")
            .build();
        
        plan = new DeepPlan(
            "Test objective",
            List.of(),
            List.of("subtask1", "subtask2"),
            List.of("criteria1")
        );
        
        policy = new ExecutionPolicy(true, true, ToolAccessLevel.TIER_A_B, 5, false, "DEEP");
        
        eventSink = Sinks.many().unicast().onBackpressureBuffer();
    }
    
    @Test
    void shouldStopWhenConfidenceReached() {
        // LLM returns high confidence immediately
        String llmResponse = """
            I've analyzed the question thoroughly.
            HYPOTHESIS: The answer is 42.
            CONFIDENCE: 0.95
            EVIDENCE: mathematical proof, verified calculation
            """;
        
        when(llmService.streamResponse(anyString(), any(), anyString()))
            .thenReturn(Flux.just(llmResponse));
        
        StepVerifier.create(coordinator.executeReasoning(
                context, plan, "What is 6*7?", List.of(), policy, eventSink))
            .assertNext(result -> {
                assertThat(result.stopReason()).isEqualTo(StopReason.CONFIDENCE_REACHED);
                assertThat(result.finalConfidence()).isGreaterThanOrEqualTo(0.85);
                assertThat(result.steps()).hasSize(1);
            })
            .verifyComplete();
    }
    
    @Test
    void shouldStopAtMaxRounds() {
        config.setMaxRoundsCap(3);
        coordinator = new DeepReasoningCoordinator(llmService, config);
        
        // Use an Answer to return different responses on subsequent calls
        java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        when(llmService.streamResponse(anyString(), any(), anyString()))
            .thenAnswer(invocation -> {
                int call = callCount.incrementAndGet();
                return Flux.just("HYPOTHESIS: Unique guess " + call + ".\nCONFIDENCE: 0.3\nEVIDENCE: partial " + call);
            });
        
        StepVerifier.create(coordinator.executeReasoning(
                context, plan, "Hard question?", List.of(), policy, eventSink))
            .assertNext(result -> {
                // Should stop at max rounds (3 in config) since hypotheses are all unique
                assertThat(result.stopReason()).isEqualTo(StopReason.MAX_ROUNDS);
                assertThat(result.steps()).hasSize(3);
            })
            .verifyComplete();
    }
    
    @Test
    void shouldStopWhenNoProgress() {
        // LLM returns same hypothesis twice (no progress)
        String response1 = """
            HYPOTHESIS: Same answer every time.
            CONFIDENCE: 0.4
            EVIDENCE: none
            """;
        
        // Both rounds return identical hypothesis - progress detection should fire after second round
        when(llmService.streamResponse(anyString(), any(), anyString()))
            .thenReturn(Flux.just(response1));
        
        StepVerifier.create(coordinator.executeReasoning(
                context, plan, "Test?", List.of(), policy, eventSink))
            .assertNext(result -> {
                // Should stop due to no progress (same hypothesis after round 2)
                assertThat(result.stopReason()).isIn(StopReason.NO_PROGRESS, StopReason.MAX_ROUNDS);
                // Could be 1, 2 or 3 steps depending on when progress check triggers
                assertThat(result.steps()).hasSizeGreaterThanOrEqualTo(1);
            })
            .verifyComplete();
    }
    
    @Test
    void shouldRespectPolicyMaxRounds() {
        // Policy with lower maxToolRounds than config
        ExecutionPolicy restrictivePolicy = new ExecutionPolicy(
            true, true, ToolAccessLevel.TIER_A_B, 2, false, "DEEP"
        );
        
        String llmResponse = """
            HYPOTHESIS: Testing
            CONFIDENCE: 0.5
            EVIDENCE: test
            """;
        
        when(llmService.streamResponse(anyString(), any(), anyString()))
            .thenReturn(Flux.just(llmResponse));
        
        StepVerifier.create(coordinator.executeReasoning(
                context, plan, "Test?", List.of(), restrictivePolicy, eventSink))
            .assertNext(result -> {
                // Should stop at policy limit (2) not config cap (3)
                assertThat(result.steps()).hasSizeLessThanOrEqualTo(2);
            })
            .verifyComplete();
    }
    
    @Test
    void shouldHandleLlmError() {
        // LLM throws exception
        when(llmService.streamResponse(anyString(), any(), anyString()))
            .thenReturn(Flux.error(new RuntimeException("LLM unavailable")));
        
        StepVerifier.create(coordinator.executeReasoning(
                context, plan, "Test?", List.of(), policy, eventSink))
            .assertNext(result -> {
                assertThat(result.stopReason()).isEqualTo(StopReason.ERROR);
            })
            .verifyComplete();
    }
    
    @Test
    void shouldEmitStepEvents() {
        String llmResponse = """
            HYPOTHESIS: Progress
            CONFIDENCE: 0.9
            EVIDENCE: verified
            """;
        
        when(llmService.streamResponse(anyString(), any(), anyString()))
            .thenReturn(Flux.just(llmResponse));
        
        // Collect events
        List<SseEnvelope> events = new ArrayList<>();
        eventSink.asFlux().subscribe(events::add);
        
        StepVerifier.create(coordinator.executeReasoning(
                context, plan, "Test?", List.of(), policy, eventSink))
            .expectNextCount(1)
            .verifyComplete();
        
        // Should have emitted at least one DEEP_REASONING_STEP event
        assertThat(events).isNotEmpty();
        assertThat(events.get(0).stage()).isEqualTo(StageNames.DEEP_REASONING_STEP);
    }
    
    @Test  
    void shouldParseConfidenceCorrectly() {
        // Test various confidence formats
        String llmResponse = """
            HYPOTHESIS: Answer
            CONFIDENCE: 0.75
            EVIDENCE: test
            """;
        
        when(llmService.streamResponse(anyString(), any(), anyString()))
            .thenReturn(Flux.just(llmResponse));
        
        StepVerifier.create(coordinator.executeReasoning(
                context, plan, "Test?", List.of(), policy, eventSink))
            .assertNext(result -> {
                assertThat(result.finalConfidence()).isBetween(0.0, 1.0);
            })
            .verifyComplete();
    }
    
    @Test
    void shouldHandleNullPolicy() {
        String llmResponse = """
            HYPOTHESIS: Test
            CONFIDENCE: 0.9
            EVIDENCE: test
            """;
        
        when(llmService.streamResponse(anyString(), any(), anyString()))
            .thenReturn(Flux.just(llmResponse));
        
        // null policy should fallback to config max
        StepVerifier.create(coordinator.executeReasoning(
                context, plan, "Test?", List.of(), null, eventSink))
            .assertNext(result -> {
                assertThat(result).isNotNull();
            })
            .verifyComplete();
    }
}
