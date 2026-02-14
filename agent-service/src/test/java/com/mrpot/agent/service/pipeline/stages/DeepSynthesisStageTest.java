package com.mrpot.agent.service.pipeline.stages;

import com.mrpot.agent.common.api.RagAnswerRequest;
import com.mrpot.agent.common.deep.VerificationReport;
import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.service.pipeline.PipelineContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DeepSynthesisStage.
 * Tests simulated streaming behavior and answer synthesis.
 */
class DeepSynthesisStageTest {
    
    private DeepSynthesisStage stage;
    private PipelineContext context;
    
    @BeforeEach
    void setUp() {
        stage = new DeepSynthesisStage();
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
    void shouldStreamAnswerInChunksFollowedBySynthesisEvent() {
        // Setup
        context = createContext("What is AI?");
        
        // Set up reasoning result with a longer response to ensure multiple chunks
        String longResponse = "Artificial Intelligence (AI) is a branch of computer science that aims to create " +
            "intelligent machines that can perform tasks typically requiring human intelligence. " +
            "This includes learning, reasoning, problem-solving, perception, and language understanding.";
        
        context.setDeepReasoning(Map.of(
            "fullResponse", longResponse
        ));
        
        // Execute and verify streaming
        Flux<SseEnvelope> resultFlux = stage.process(null, context).block();
        
        AtomicInteger answerDeltaCount = new AtomicInteger(0);
        AtomicInteger synthesisCount = new AtomicInteger(0);
        StringBuilder reconstructedAnswer = new StringBuilder();
        
        StepVerifier.create(resultFlux)
            .thenConsumeWhile(envelope -> {
                if (StageNames.ANSWER_DELTA.equals(envelope.stage())) {
                    answerDeltaCount.incrementAndGet();
                    reconstructedAnswer.append(envelope.message());
                    
                    // Verify delta payload structure
                    assertThat(envelope.payload()).isInstanceOf(Map.class);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = (Map<String, Object>) envelope.payload();
                    assertThat(payload).containsKey("delta");
                    
                    return true;
                } else if (StageNames.DEEP_SYNTHESIS.equals(envelope.stage())) {
                    synthesisCount.incrementAndGet();
                    
                    // Verify synthesis payload structure
                    assertThat(envelope.payload()).isInstanceOf(Map.class);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = (Map<String, Object>) envelope.payload();
                    assertThat(payload).containsKeys("status", "uiBlocks");
                    assertThat(payload.get("status")).isEqualTo("complete");
                    
                    return true;
                }
                return false;
            })
            .verifyComplete();
        
        // Verify multiple answer_delta events were emitted
        assertThat(answerDeltaCount.get()).isGreaterThan(1);
        
        // Verify exactly one synthesis event at the end
        assertThat(synthesisCount.get()).isEqualTo(1);
        
        // Verify final answer is stored in context
        assertThat(context.getFinalAnswer()).contains("Artificial Intelligence");
    }
    
    @Test
    void shouldHandleShortAnswerWithSingleChunk() {
        // Setup
        context = createContext("Hi");
        
        // Short response that fits in one chunk
        context.setDeepReasoning(Map.of(
            "fullResponse", "Hello!"
        ));
        
        // Execute
        Flux<SseEnvelope> resultFlux = stage.process(null, context).block();
        
        AtomicInteger deltaCount = new AtomicInteger(0);
        AtomicInteger synthesisCount = new AtomicInteger(0);
        
        StepVerifier.create(resultFlux)
            .thenConsumeWhile(envelope -> {
                if (StageNames.ANSWER_DELTA.equals(envelope.stage())) {
                    deltaCount.incrementAndGet();
                } else if (StageNames.DEEP_SYNTHESIS.equals(envelope.stage())) {
                    synthesisCount.incrementAndGet();
                }
                return true;
            })
            .verifyComplete();
        
        // Should have at least 1 delta event and exactly 1 synthesis event
        assertThat(deltaCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(synthesisCount.get()).isEqualTo(1);
    }
    
    @Test
    void shouldIncludeUnresolvedClaimsInAnswer() {
        // Setup
        context = createContext("Complex question");
        
        context.setDeepReasoning(Map.of(
            "fullResponse", "Based on the analysis..."
        ));
        
        // Set verification report with unresolved claims
        VerificationReport report = VerificationReport.withIssues(
            0.7,  // consistencyScore
            List.of(),  // contradictions
            List.of(),  // factualityFlags
            List.of("Claim 2 needs more evidence"),  // unresolvedClaims
            List.of("Some issues"),  // issues
            List.of(),  // recommendations
            0.6   // confidence
        );
        context.setVerificationReport(report);
        
        // Execute
        Flux<SseEnvelope> resultFlux = stage.process(null, context).block();
        
        StepVerifier.create(resultFlux)
            .thenConsumeWhile(envelope -> true)
            .verifyComplete();
        
        // Verify answer includes unresolved claims section
        String finalAnswer = context.getFinalAnswer();
        assertThat(finalAnswer).contains("The following issues have not been fully resolved");
        assertThat(finalAnswer).contains("Claim 2 needs more evidence");
    }
    
    @Test
    void shouldUseDefaultAnswerWhenNoReasoningAvailable() {
        // Setup - no reasoning set
        context = createContext("Test question");
        
        // Execute
        Flux<SseEnvelope> resultFlux = stage.process(null, context).block();
        
        StepVerifier.create(resultFlux)
            .thenConsumeWhile(envelope -> true)
            .verifyComplete();
        
        // Should use default answer
        assertThat(context.getFinalAnswer()).contains("I couldn't produce a complete deep synthesis");
    }
    
    @Test
    void shouldHaveIncreasingSequenceNumbers() {
        // Setup
        context = createContext("Test sequence");
        
        String response = "This is a test response that should be split into multiple chunks for streaming.";
        context.setDeepReasoning(Map.of("fullResponse", response));
        
        // Execute
        Flux<SseEnvelope> resultFlux = stage.process(null, context).block();
        
        java.util.concurrent.atomic.AtomicLong lastSeq = new java.util.concurrent.atomic.AtomicLong(-1);
        
        StepVerifier.create(resultFlux)
            .thenConsumeWhile(envelope -> {
                long currentSeq = envelope.seq();
                assertThat(currentSeq).isGreaterThan(lastSeq.get());
                lastSeq.set(currentSeq);
                return true;
            })
            .verifyComplete();
    }
}
