package com.mrpot.agent.service.pipeline;

import com.mrpot.agent.common.api.RagAnswerRequest;
import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.policy.ExecutionPolicy;
import com.mrpot.agent.service.policy.ModeDecider;
import com.mrpot.agent.service.policy.PolicyBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PipelineFactory.
 * Tests pipeline context creation, pipeline selection, and mode resolution logic.
 */
class PipelineFactoryTest {

    private FastPipeline fastPipeline;
    private DeepPipeline deepPipeline;
    private PolicyBuilder policyBuilder;
    private ModeDecider modeDecider;
    private PipelineFactory pipelineFactory;
    
    private ExecutionPolicy mockPolicy;
    private PipelineRunner mockRunner;

    @BeforeEach
    void setUp() {
        fastPipeline = Mockito.mock(FastPipeline.class);
        deepPipeline = Mockito.mock(DeepPipeline.class);
        policyBuilder = Mockito.mock(PolicyBuilder.class);
        modeDecider = Mockito.mock(ModeDecider.class);
        
        mockPolicy = Mockito.mock(ExecutionPolicy.class);
        mockRunner = Mockito.mock(PipelineRunner.class);
        
        pipelineFactory = new PipelineFactory(fastPipeline, deepPipeline, policyBuilder, modeDecider);
        
        // Default stub behavior
        when(policyBuilder.build(any())).thenReturn(mockPolicy);
        when(fastPipeline.build()).thenReturn(mockRunner);
        when(deepPipeline.build()).thenReturn(mockRunner);
    }

    @Test
    void createContext_withGeneralScopeMode_createsContextCorrectly() {
        // Arrange
        RagAnswerRequest request = createMockRequest(ScopeMode.GENERAL, "session123");
        when(modeDecider.decide(any(), any())).thenReturn("FAST");
        
        // Act
        PipelineContext context = pipelineFactory.createContext(request, "trace456");
        
        // Assert
        assertNotNull(context);
        assertNotNull(context.runId());
        assertEquals("trace456", context.traceId());
        assertEquals("session123", context.sessionId());
        assertEquals("userId_placeholder", context.userId());
        assertEquals(ScopeMode.GENERAL, context.scopeMode());
        assertEquals(mockPolicy, context.policy());
        assertEquals("FAST", context.executionMode());
        assertEquals(request, context.request());
        
        verify(policyBuilder).build(ScopeMode.GENERAL);
        verify(modeDecider).decide(request, mockPolicy);
    }

    @Test
    void createContext_withNullScopeMode_defaultsToGeneral() {
        // Arrange
        RagAnswerRequest request = createMockRequest(null, "session123");
        when(modeDecider.decide(any(), any())).thenReturn("FAST");
        
        // Act
        PipelineContext context = pipelineFactory.createContext(request, "trace456");
        
        // Assert
        assertEquals(ScopeMode.GENERAL, context.scopeMode());
        verify(policyBuilder).build(ScopeMode.GENERAL);
    }

    @Test
    void createContext_withAutoScopeMode_defaultsToGeneral() {
        // Arrange
        RagAnswerRequest request = createMockRequest(ScopeMode.AUTO, "session123");
        when(modeDecider.decide(any(), any())).thenReturn("FAST");
        
        // Act
        PipelineContext context = pipelineFactory.createContext(request, "trace456");
        
        // Assert
        assertEquals(ScopeMode.GENERAL, context.scopeMode());
        verify(policyBuilder).build(ScopeMode.GENERAL);
    }

    @Test
    void createContext_withOwnerOnlyScopeMode_preservesScopeMode() {
        // Arrange
        RagAnswerRequest request = createMockRequest(ScopeMode.OWNER_ONLY, "session123");
        when(modeDecider.decide(any(), any())).thenReturn("FAST");
        
        // Act
        PipelineContext context = pipelineFactory.createContext(request, "trace456");
        
        // Assert
        assertEquals(ScopeMode.OWNER_ONLY, context.scopeMode());
        verify(policyBuilder).build(ScopeMode.OWNER_ONLY);
    }

    @Test
    void createContext_withPrivacySafeScopeMode_preservesScopeMode() {
        // Arrange
        RagAnswerRequest request = createMockRequest(ScopeMode.PRIVACY_SAFE, "session123");
        when(modeDecider.decide(any(), any())).thenReturn("FAST");
        
        // Act
        PipelineContext context = pipelineFactory.createContext(request, "trace456");
        
        // Assert
        assertEquals(ScopeMode.PRIVACY_SAFE, context.scopeMode());
        verify(policyBuilder).build(ScopeMode.PRIVACY_SAFE);
    }

    @Test
    void createContext_withDeepMode_setsExecutionModeCorrectly() {
        // Arrange
        RagAnswerRequest request = createMockRequest(ScopeMode.GENERAL, "session123");
        when(modeDecider.decide(any(), any())).thenReturn("DEEP");
        
        // Act
        PipelineContext context = pipelineFactory.createContext(request, "trace456");
        
        // Assert
        assertEquals("DEEP", context.executionMode());
    }

    @Test
    void createPipeline_withFastMode_returnsFastPipeline() {
        // Arrange
        PipelineContext context = createMockContext("FAST");
        
        // Act
        PipelineRunner result = pipelineFactory.createPipeline(context);
        
        // Assert
        assertSame(mockRunner, result);
        verify(fastPipeline).build();
    }

    @Test
    void createPipeline_withDeepMode_usesDeepPipeline() {
        // Arrange
        PipelineContext context = createMockContext("DEEP");
        
        // Act
        PipelineRunner result = pipelineFactory.createPipeline(context);
        
        // Assert
        assertSame(mockRunner, result);
        verify(deepPipeline).build();
    }

    @Test
    void createPipeline_withUnknownMode_defaultsToFastPipeline() {
        // Arrange
        PipelineContext context = createMockContext("UNKNOWN_MODE");
        
        // Act
        PipelineRunner result = pipelineFactory.createPipeline(context);
        
        // Assert
        assertSame(mockRunner, result);
        verify(fastPipeline).build();
    }

    @Test
    void create_createsContextAndPipeline() {
        // Arrange
        RagAnswerRequest request = createMockRequest(ScopeMode.GENERAL, "session123");
        when(modeDecider.decide(any(), any())).thenReturn("FAST");
        
        // Act
        PipelineFactory.PipelineCreationResult result = pipelineFactory.create(request, "trace456");
        
        // Assert
        assertNotNull(result);
        assertNotNull(result.context());
        assertNotNull(result.pipeline());
        assertSame(mockRunner, result.pipeline());
        
        assertEquals("trace456", result.context().traceId());
        assertEquals("session123", result.context().sessionId());
        assertEquals(ScopeMode.GENERAL, result.context().scopeMode());
        assertEquals("FAST", result.context().executionMode());
        
        verify(policyBuilder).build(ScopeMode.GENERAL);
        verify(modeDecider).decide(request, mockPolicy);
        verify(fastPipeline).build();
    }

    @Test
    void createContext_generatesUniqueRunId() {
        // Arrange
        RagAnswerRequest request = createMockRequest(ScopeMode.GENERAL, "session123");
        when(modeDecider.decide(any(), any())).thenReturn("FAST");
        
        // Act
        PipelineContext context1 = pipelineFactory.createContext(request, "trace1");
        PipelineContext context2 = pipelineFactory.createContext(request, "trace2");
        
        // Assert
        assertNotNull(context1.runId());
        assertNotNull(context2.runId());
        assertNotEquals(context1.runId(), context2.runId());
    }

    @Test
    void createContext_withMultipleCalls_createsIndependentContexts() {
        // Arrange
        RagAnswerRequest request1 = createMockRequest(ScopeMode.GENERAL, "session1");
        RagAnswerRequest request2 = createMockRequest(ScopeMode.OWNER_ONLY, "session2");
        when(modeDecider.decide(any(), any())).thenReturn("FAST");
        
        // Act
        PipelineContext context1 = pipelineFactory.createContext(request1, "trace1");
        PipelineContext context2 = pipelineFactory.createContext(request2, "trace2");
        
        // Assert
        assertEquals("session1", context1.sessionId());
        assertEquals("session2", context2.sessionId());
        assertEquals(ScopeMode.GENERAL, context1.scopeMode());
        assertEquals(ScopeMode.OWNER_ONLY, context2.scopeMode());
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
