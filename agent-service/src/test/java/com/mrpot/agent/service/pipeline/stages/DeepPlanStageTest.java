package com.mrpot.agent.service.pipeline.stages;

import com.mrpot.agent.common.api.RagAnswerRequest;
import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.service.LlmService;
import com.mrpot.agent.service.pipeline.DeepArtifactStore;
import com.mrpot.agent.service.pipeline.DeepModeConfig;
import com.mrpot.agent.service.pipeline.PipelineContext;
import com.mrpot.agent.service.pipeline.artifacts.DeepPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DeepPlanStage.
 * Tests LLM-based plan generation and fallback handling.
 */
@ExtendWith(MockitoExtension.class)
class DeepPlanStageTest {
    
    @Mock
    private LlmService llmService;
    
    private DeepModeConfig config;
    private DeepPlanStage stage;
    private PipelineContext context;
    
    @BeforeEach
    void setUp() {
        config = new DeepModeConfig();
        config.setPlanTimeoutSeconds(10);
        
        stage = new DeepPlanStage(llmService, config);
    }
    
    private PipelineContext createContext(String question) {
        RagAnswerRequest request = new RagAnswerRequest(
            question,
            "test-session",
            null,
            null,
            null,
            null,
            "DEEP",
            null,
            null
        );
        return PipelineContext.builder()
            .runId("test-run")
            .sessionId("test-session")
            .traceId("test-trace")
            .request(request)
            .executionMode("DEEP")
            .build();
    }
    
    @Test
    void shouldGeneratePlanFromLlmResponse() {
        // Setup
        context = createContext("What is the meaning of life?");
        context.put(PipelineContext.KEY_HISTORY, List.of());
        
        String llmResponse = """
            OBJECTIVE: Explore philosophical and practical meanings of life.
            CONSTRAINTS: Keep response concise, avoid religious bias
            SUBTASKS: Review philosophy, Consider personal meaning, Synthesize
            SUCCESS_CRITERIA: Provide balanced perspective, Include actionable insights
            """;
        
        when(llmService.streamResponse(anyString(), any(), anyString()))
            .thenReturn(Flux.just(llmResponse));
        
        // Execute
        StepVerifier.create(stage.process(null, context))
            .assertNext(envelope -> {
                assertThat(envelope.stage()).isEqualTo(StageNames.DEEP_PLAN_DONE);
                assertThat(envelope.message()).contains("Plan");
            })
            .verifyComplete();
        
        // Verify plan was stored
        DeepArtifactStore store = new DeepArtifactStore(context);
        DeepPlan plan = store.getPlan();
        
        assertThat(plan).isNotNull();
        assertThat(plan.objective()).contains("meaning");
        assertThat(plan.subtasks()).hasSizeGreaterThanOrEqualTo(1);
    }
    
    @Test
    void shouldUseFallbackWhenEmptyQuestion() {
        // No question in context
        context = createContext("");
        
        // Execute
        StepVerifier.create(stage.process(null, context))
            .assertNext(envelope -> {
                assertThat(envelope.stage()).isEqualTo(StageNames.DEEP_PLAN_DONE);
                @SuppressWarnings("unchecked")
                var payload = (java.util.Map<String, Object>) envelope.payload();
                assertThat(payload.get("status")).isEqualTo("fallback");
            })
            .verifyComplete();
        
        // Verify fallback plan was stored
        DeepArtifactStore store = new DeepArtifactStore(context);
        DeepPlan plan = store.getPlan();
        
        assertThat(plan).isNotNull();
        assertThat(plan.subtasks()).contains("Direct response");
    }
    
    @Test
    void shouldUseFallbackOnLlmError() {
        context = createContext("Test question");
        context.put(PipelineContext.KEY_HISTORY, List.of());
        
        when(llmService.streamResponse(anyString(), any(), anyString()))
            .thenReturn(Flux.error(new RuntimeException("LLM unavailable")));
        
        // Execute
        StepVerifier.create(stage.process(null, context))
            .assertNext(envelope -> {
                assertThat(envelope.stage()).isEqualTo(StageNames.DEEP_PLAN_DONE);
                @SuppressWarnings("unchecked")
                var payload = (java.util.Map<String, Object>) envelope.payload();
                assertThat(payload.get("status")).isEqualTo("fallback");
            })
            .verifyComplete();
        
        // Verify fallback plan was stored
        DeepArtifactStore store = new DeepArtifactStore(context);
        assertThat(store.getPlan()).isNotNull();
    }
    
    @Test
    void shouldHandleNullHistory() {
        context = createContext("Test");
        // No history set (null)
        
        String llmResponse = """
            OBJECTIVE: Test
            CONSTRAINTS: NONE
            SUBTASKS: Analyze
            SUCCESS_CRITERIA: Complete
            """;
        
        when(llmService.streamResponse(anyString(), any(), anyString()))
            .thenReturn(Flux.just(llmResponse));
        
        StepVerifier.create(stage.process(null, context))
            .assertNext(envelope -> {
                assertThat(envelope.stage()).isEqualTo(StageNames.DEEP_PLAN_DONE);
            })
            .verifyComplete();
    }
    
    @Test
    void shouldFilterOutNoneConstraints() {
        context = createContext("Simple question");
        context.put(PipelineContext.KEY_HISTORY, List.of());
        
        String llmResponse = """
            OBJECTIVE: Answer simple question
            CONSTRAINTS: NONE
            SUBTASKS: Respond directly
            SUCCESS_CRITERIA: Accurate answer
            """;
        
        when(llmService.streamResponse(anyString(), any(), anyString()))
            .thenReturn(Flux.just(llmResponse));
        
        StepVerifier.create(stage.process(null, context))
            .expectNextCount(1)
            .verifyComplete();
        
        DeepArtifactStore store = new DeepArtifactStore(context);
        DeepPlan plan = store.getPlan();
        
        assertThat(plan.constraints()).isEmpty(); // NONE should be filtered
    }
    
    @Test
    void shouldParseMalformedResponseGracefully() {
        context = createContext("Test");
        context.put(PipelineContext.KEY_HISTORY, List.of());
        
        // Malformed response without proper format
        String llmResponse = "I'll just answer directly without the format.";
        
        when(llmService.streamResponse(anyString(), any(), anyString()))
            .thenReturn(Flux.just(llmResponse));
        
        StepVerifier.create(stage.process(null, context))
            .assertNext(envelope -> {
                assertThat(envelope.stage()).isEqualTo(StageNames.DEEP_PLAN_DONE);
            })
            .verifyComplete();
        
        // Should still create a plan with defaults
        DeepArtifactStore store = new DeepArtifactStore(context);
        DeepPlan plan = store.getPlan();
        
        assertThat(plan).isNotNull();
        assertThat(plan.subtasks()).isNotEmpty(); // Should have default subtask
    }
    
    @Test
    void shouldIncludeSequenceNumberInEnvelope() {
        context = createContext("Test");
        context.put(PipelineContext.KEY_HISTORY, List.of());
        
        String llmResponse = """
            OBJECTIVE: Test
            CONSTRAINTS: NONE
            SUBTASKS: Do
            SUCCESS_CRITERIA: Done
            """;
        
        when(llmService.streamResponse(anyString(), any(), anyString()))
            .thenReturn(Flux.just(llmResponse));
        
        StepVerifier.create(stage.process(null, context))
            .assertNext(envelope -> {
                assertThat(envelope.seq()).isGreaterThan(0);
                assertThat(envelope.ts()).isGreaterThan(0);
                assertThat(envelope.traceId()).isEqualTo("test-trace");
                assertThat(envelope.sessionId()).isEqualTo("test-session");
            })
            .verifyComplete();
    }
}
