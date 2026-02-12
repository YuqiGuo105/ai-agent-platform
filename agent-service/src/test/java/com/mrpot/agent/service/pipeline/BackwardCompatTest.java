package com.mrpot.agent.service.pipeline;

import com.mrpot.agent.common.api.RagAnswerRequest;
import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.policy.ExecutionPolicy;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.service.policy.ModeDecider;
import com.mrpot.agent.service.policy.PolicyBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Backward compatibility tests to ensure FAST mode is not affected
 * by the addition of DEEP pipeline.
 */
@DisplayName("Backward Compatibility Tests")
class BackwardCompatTest {

    private FastPipeline fastPipeline;
    private DeepPipeline deepPipeline;
    private PolicyBuilder policyBuilder;
    private ModeDecider modeDecider;
    private PipelineFactory pipelineFactory;
    
    private ExecutionPolicy mockPolicy;
    private PipelineRunner mockFastRunner;
    private PipelineRunner mockDeepRunner;

    @BeforeEach
    void setUp() {
        fastPipeline = Mockito.mock(FastPipeline.class);
        deepPipeline = Mockito.mock(DeepPipeline.class);
        policyBuilder = Mockito.mock(PolicyBuilder.class);
        modeDecider = Mockito.mock(ModeDecider.class);
        
        mockPolicy = Mockito.mock(ExecutionPolicy.class);
        mockFastRunner = Mockito.mock(PipelineRunner.class);
        mockDeepRunner = Mockito.mock(PipelineRunner.class);
        
        pipelineFactory = new PipelineFactory(
            fastPipeline, 
            deepPipeline, 
            policyBuilder, 
            modeDecider
        );
        
        // Default stub behavior
        when(policyBuilder.build(any())).thenReturn(mockPolicy);
        when(fastPipeline.build()).thenReturn(mockFastRunner);
        when(deepPipeline.build()).thenReturn(mockDeepRunner);
    }

    @Test
    @DisplayName("FAST mode pipeline selection is not affected by DEEP implementation")
    void createPipeline_withFastMode_returnsFastPipeline() {
        // Arrange
        PipelineContext context = createMockContext("FAST");
        
        // Act
        PipelineRunner result = pipelineFactory.createPipeline(context);
        
        // Assert - FAST mode should still work as before
        assertSame(mockFastRunner, result);
        verify(fastPipeline).build();
        verify(deepPipeline, never()).build();
    }

    @Test
    @DisplayName("FAST mode context creation is not affected")
    void createContext_withFastMode_createsContextCorrectly() {
        // Arrange
        RagAnswerRequest request = createMockRequest(ScopeMode.GENERAL, "session123");
        when(modeDecider.decide(any(), any())).thenReturn("FAST");
        
        // Act
        PipelineContext context = pipelineFactory.createContext(request, "trace456");
        
        // Assert
        assertNotNull(context);
        assertEquals("FAST", context.executionMode());
        assertEquals(ScopeMode.GENERAL, context.scopeMode());
        assertEquals("session123", context.sessionId());
    }

    @Test
    @DisplayName("FAST mode create() method works correctly")
    void create_withFastMode_createsFastPipeline() {
        // Arrange
        RagAnswerRequest request = createMockRequest(ScopeMode.GENERAL, "session123");
        when(modeDecider.decide(any(), any())).thenReturn("FAST");
        
        // Act
        PipelineFactory.PipelineCreationResult result = pipelineFactory.create(request, "trace456");
        
        // Assert
        assertNotNull(result);
        assertSame(mockFastRunner, result.pipeline());
        assertEquals("FAST", result.context().executionMode());
        
        verify(fastPipeline).build();
        verify(deepPipeline, never()).build();
    }

    @Test
    @DisplayName("Default mode fallback still works (unknown mode -> FAST)")
    void createPipeline_withUnknownMode_defaultsToFast() {
        // Arrange
        PipelineContext context = createMockContext("UNKNOWN");
        
        // Act
        PipelineRunner result = pipelineFactory.createPipeline(context);
        
        // Assert
        assertSame(mockFastRunner, result);
        verify(fastPipeline).build();
        verify(deepPipeline, never()).build();
    }

    @Test
    @DisplayName("Existing scope modes are still supported")
    void createContext_withDifferentScopeModes_worksCorrectly() {
        // Test GENERAL
        RagAnswerRequest generalRequest = createMockRequest(ScopeMode.GENERAL, "session1");
        when(modeDecider.decide(any(), any())).thenReturn("FAST");
        PipelineContext generalContext = pipelineFactory.createContext(generalRequest, "trace1");
        assertEquals(ScopeMode.GENERAL, generalContext.scopeMode());
        
        // Test OWNER_ONLY
        RagAnswerRequest ownerRequest = createMockRequest(ScopeMode.OWNER_ONLY, "session2");
        PipelineContext ownerContext = pipelineFactory.createContext(ownerRequest, "trace2");
        assertEquals(ScopeMode.OWNER_ONLY, ownerContext.scopeMode());
        
        // Test PRIVACY_SAFE
        RagAnswerRequest privacyRequest = createMockRequest(ScopeMode.PRIVACY_SAFE, "session3");
        PipelineContext privacyContext = pipelineFactory.createContext(privacyRequest, "trace3");
        assertEquals(ScopeMode.PRIVACY_SAFE, privacyContext.scopeMode());
    }

    @Test
    @DisplayName("Existing StageNames constants are preserved")
    void stageNames_existingConstantsArePreserved() {
        // Verify existing stage names are not changed
        assertEquals("answer_delta", StageNames.ANSWER_DELTA);
        assertEquals("answer_final", StageNames.ANSWER_FINAL);
        assertEquals("start", StageNames.START);
        assertEquals("plan", StageNames.PLAN);
        assertEquals("History", StageNames.REDIS);
        assertEquals("file_extract", StageNames.FILE_EXTRACT);
        assertEquals("Searching", StageNames.RAG);
        assertEquals("error", StageNames.ERROR);
    }

    @Test
    @DisplayName("New DEEP stage names are added without affecting existing ones")
    void stageNames_newDeepConstantsAreAdded() {
        // Verify new DEEP stage names exist
        assertEquals("deep_plan", StageNames.DEEP_PLAN);
        assertEquals("deep_reasoning", StageNames.DEEP_REASONING);
        assertEquals("deep_synthesis", StageNames.DEEP_SYNTHESIS);
    }

    @Test
    @DisplayName("PipelineContext existing methods work correctly")
    void pipelineContext_existingMethodsWork() {
        // Arrange
        RagAnswerRequest request = createMockRequest(ScopeMode.GENERAL, "session123");
        when(request.question()).thenReturn("test question");
        
        PipelineContext context = PipelineContext.builder()
            .runId("run-123")
            .traceId("trace-456")
            .sessionId("session-789")
            .userId("user-001")
            .request(request)
            .scopeMode(ScopeMode.GENERAL)
            .policy(mockPolicy)
            .executionMode("FAST")
            .build();
        
        // Assert existing methods work
        assertEquals("run-123", context.runId());
        assertEquals("trace-456", context.traceId());
        assertEquals("session-789", context.sessionId());
        assertEquals("user-001", context.userId());
        assertEquals(ScopeMode.GENERAL, context.scopeMode());
        assertEquals("FAST", context.executionMode());
        
        // Test existing working memory methods
        context.setFinalAnswer("test answer");
        assertEquals("test answer", context.getFinalAnswer());
        
        // Test nextSeq()
        long seq1 = context.nextSeq();
        long seq2 = context.nextSeq();
        assertEquals(1, seq1);
        assertEquals(2, seq2);
    }

    // Helper methods

    private RagAnswerRequest createMockRequest(ScopeMode scopeMode, String sessionId) {
        RagAnswerRequest request = Mockito.mock(RagAnswerRequest.class);
        when(request.scopeMode()).thenReturn(scopeMode);
        when(request.sessionId()).thenReturn(sessionId);
        when(request.question()).thenReturn("test question");
        return request;
    }

    private PipelineContext createMockContext(String executionMode) {
        PipelineContext context = Mockito.mock(PipelineContext.class);
        when(context.executionMode()).thenReturn(executionMode);
        return context;
    }
}
