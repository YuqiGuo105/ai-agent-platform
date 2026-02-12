package com.mrpot.agent.service.pipeline;

import com.mrpot.agent.common.api.RagAnswerRequest;
import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.policy.ExecutionPolicy;
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
 * Unit tests for PipelineFactory routing logic.
 * Specifically tests pipeline selection based on execution mode.
 */
@DisplayName("PipelineFactory Routing Tests")
class PipelineFactoryRoutingTest {

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
    @DisplayName("DEEP mode should select deepPipeline.build()")
    void createPipeline_withDeepMode_selectsDeepPipeline() {
        // Arrange
        PipelineContext context = createMockContext("DEEP");
        
        // Act
        PipelineRunner result = pipelineFactory.createPipeline(context);
        
        // Assert
        assertSame(mockDeepRunner, result);
        verify(deepPipeline).build();
        verify(fastPipeline, never()).build();
    }

    @Test
    @DisplayName("FAST mode should select fastPipeline.build()")
    void createPipeline_withFastMode_selectsFastPipeline() {
        // Arrange
        PipelineContext context = createMockContext("FAST");
        
        // Act
        PipelineRunner result = pipelineFactory.createPipeline(context);
        
        // Assert
        assertSame(mockFastRunner, result);
        verify(fastPipeline).build();
        verify(deepPipeline, never()).build();
    }

    @Test
    @DisplayName("Unknown mode should fallback to FAST pipeline")
    void createPipeline_withUnknownMode_fallbacksToFast() {
        // Arrange
        PipelineContext context = createMockContext("UNKNOWN_MODE");
        
        // Act
        PipelineRunner result = pipelineFactory.createPipeline(context);
        
        // Assert
        assertSame(mockFastRunner, result);
        verify(fastPipeline).build();
        verify(deepPipeline, never()).build();
    }

    @Test
    @DisplayName("Empty mode should fallback to FAST pipeline")
    void createPipeline_withEmptyMode_fallbacksToFast() {
        // Arrange
        PipelineContext context = createMockContext("");
        
        // Act
        PipelineRunner result = pipelineFactory.createPipeline(context);
        
        // Assert
        assertSame(mockFastRunner, result);
        verify(fastPipeline).build();
        verify(deepPipeline, never()).build();
    }

    @Test
    @DisplayName("Lowercase 'deep' should fallback to FAST (case sensitive)")
    void createPipeline_withLowercaseDeep_fallbacksToFast() {
        // Arrange
        PipelineContext context = createMockContext("deep");
        
        // Act
        PipelineRunner result = pipelineFactory.createPipeline(context);
        
        // Assert
        assertSame(mockFastRunner, result);
        verify(fastPipeline).build();
        verify(deepPipeline, never()).build();
    }

    @Test
    @DisplayName("DEEP mode request through create() method should use deepPipeline")
    void create_withDeepMode_createsDeepPipeline() {
        // Arrange
        RagAnswerRequest request = createMockRequest(ScopeMode.GENERAL, "session123");
        when(modeDecider.decide(any(), any())).thenReturn("DEEP");
        
        // Act
        PipelineFactory.PipelineCreationResult result = pipelineFactory.create(request, "trace456");
        
        // Assert
        assertNotNull(result);
        assertNotNull(result.context());
        assertNotNull(result.pipeline());
        assertSame(mockDeepRunner, result.pipeline());
        assertEquals("DEEP", result.context().executionMode());
        
        verify(deepPipeline).build();
        verify(fastPipeline, never()).build();
    }

    @Test
    @DisplayName("FAST mode request through create() method should use fastPipeline")
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
