package com.mrpot.agent.telemetry.controller;

import com.mrpot.agent.common.telemetry.RunDetailDto;
import com.mrpot.agent.common.telemetry.ToolCallDto;
import com.mrpot.agent.telemetry.entity.KnowledgeRunEntity;
import com.mrpot.agent.telemetry.entity.KnowledgeToolCallEntity;
import com.mrpot.agent.telemetry.repository.KnowledgeRunEventJpaRepository;
import com.mrpot.agent.telemetry.repository.KnowledgeRunJpaRepository;
import com.mrpot.agent.telemetry.repository.KnowledgeToolCallJpaRepository;
import com.mrpot.agent.telemetry.service.KbServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TraceQueryController focusing on new fields:
 * - kbDocIds
 * - historyCount
 * - recentQuestionsJson
 * - keyInfoJson
 */
class TraceQueryControllerWithNewFieldsTest {

    private KnowledgeRunJpaRepository runRepo;
    private KnowledgeToolCallJpaRepository toolCallRepo;
    private KnowledgeRunEventJpaRepository eventRepo;
    private KbServiceClient kbServiceClient;
    private TraceQueryController controller;

    @BeforeEach
    void setUp() {
        runRepo = mock(KnowledgeRunJpaRepository.class);
        toolCallRepo = mock(KnowledgeToolCallJpaRepository.class);
        eventRepo = mock(KnowledgeRunEventJpaRepository.class);
        kbServiceClient = mock(KbServiceClient.class);
        controller = new TraceQueryController(runRepo, toolCallRepo, eventRepo, kbServiceClient);
    }

    @Test
    void getRunDetail_includesNewHistoryFields() {
        // Given
        KnowledgeRunEntity run = new KnowledgeRunEntity();
        run.setId("run-123");
        run.setTraceId("trace-456");
        run.setSessionId("session-789");
        run.setMode("FAST");
        run.setModel("deepseek");
        run.setStatus("DONE");
        run.setTotalLatencyMs(1500L);
        run.setKbHitCount(5);
        run.setKbDocIds("doc-1,doc-2,doc-3");
        run.setHistoryCount(6);
        run.setRecentQuestionsJson("[\"What is AI?\",\"Tell me about ML\",\"Explain neural networks\"]");
        run.setQuestion("What are the latest AI trends?");

        when(runRepo.findById("run-123")).thenReturn(Optional.of(run));
        when(toolCallRepo.findByRunIdOrderByCreatedAt("run-123")).thenReturn(List.of());

        // When
        ResponseEntity<RunDetailDto> response = controller.getRunDetail("run-123");

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        RunDetailDto dto = response.getBody();
        assertEquals("run-123", dto.runId());
        assertEquals("trace-456", dto.traceId());
        assertEquals("session-789", dto.sessionId());
        assertEquals("FAST", dto.mode());
        assertEquals("deepseek", dto.model());
        assertEquals("DONE", dto.status());
        assertEquals(1500L, dto.totalLatencyMs());
        assertEquals(5, dto.kbHitCount());
        assertEquals("doc-1,doc-2,doc-3", dto.kbDocIds());
        assertEquals(6, dto.historyCount());
        assertEquals("[\"What is AI?\",\"Tell me about ML\",\"Explain neural networks\"]", dto.recentQuestionsJson());
        assertEquals("What are the latest AI trends?", dto.question());
        assertTrue(dto.tools().isEmpty());
    }

    @Test
    void getRunDetail_handlesNullHistoryFields() {
        // Given
        KnowledgeRunEntity run = new KnowledgeRunEntity();
        run.setId("run-123");
        run.setTraceId("trace-456");
        run.setSessionId("session-789");
        run.setMode("FAST");
        run.setModel("deepseek");
        run.setStatus("DONE");
        run.setTotalLatencyMs(1000L);
        run.setKbHitCount(0);
        run.setKbDocIds(null);
        run.setHistoryCount(null);
        run.setRecentQuestionsJson(null);
        run.setQuestion("Simple question");

        when(runRepo.findById("run-123")).thenReturn(Optional.of(run));
        when(toolCallRepo.findByRunIdOrderByCreatedAt("run-123")).thenReturn(List.of());

        // When
        ResponseEntity<RunDetailDto> response = controller.getRunDetail("run-123");

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        RunDetailDto dto = response.getBody();
        assertNull(dto.kbDocIds());
        assertNull(dto.historyCount());
        assertNull(dto.recentQuestionsJson());
    }

    @Test
    void getRunDetail_includesToolsWithKeyInfoJson() {
        // Given
        KnowledgeRunEntity run = new KnowledgeRunEntity();
        run.setId("run-123");
        run.setTraceId("trace-456");
        run.setSessionId("session-789");
        run.setMode("DEEP");
        run.setModel("deepseek");
        run.setStatus("DONE");
        run.setTotalLatencyMs(3000L);
        run.setKbHitCount(10);
        run.setKbDocIds("doc-a,doc-b");
        run.setHistoryCount(8);
        run.setRecentQuestionsJson("[\"Q1\",\"Q2\"]");
        run.setQuestion("Complex question");

        KnowledgeToolCallEntity toolCall1 = new KnowledgeToolCallEntity();
        toolCall1.setId("tc-1");
        toolCall1.setToolName("kb.search");
        toolCall1.setAttempt(1);
        toolCall1.setOk(true);
        toolCall1.setDurationMs(200L);
        toolCall1.setArgsPreview("{\"query\":\"test\"}");
        toolCall1.setResultPreview("{\"results\":[]}");
        toolCall1.setCacheHit(false);
        toolCall1.setFreshness("LIVE");
        toolCall1.setKeyInfoJson("{\"relevance\":0.95,\"sources\":[\"doc-1\",\"doc-2\"]}");
        toolCall1.setCalledAt(Instant.parse("2026-02-25T10:00:00Z"));

        KnowledgeToolCallEntity toolCall2 = new KnowledgeToolCallEntity();
        toolCall2.setId("tc-2");
        toolCall2.setToolName("reasoning.analyze");
        toolCall2.setAttempt(2);
        toolCall2.setOk(true);
        toolCall2.setDurationMs(500L);
        toolCall2.setArgsPreview("{\"data\":\"analysis\"}");
        toolCall2.setResultPreview("{\"conclusion\":\"result\"}");
        toolCall2.setCacheHit(true);
        toolCall2.setFreshness("CACHED");
        toolCall2.setKeyInfoJson("{\"confidence\":0.87,\"keyPoints\":[\"point1\",\"point2\"]}");
        toolCall2.setCalledAt(Instant.parse("2026-02-25T10:00:01Z"));

        when(runRepo.findById("run-123")).thenReturn(Optional.of(run));
        when(toolCallRepo.findByRunIdOrderByCreatedAt("run-123"))
            .thenReturn(List.of(toolCall1, toolCall2));

        // When
        ResponseEntity<RunDetailDto> response = controller.getRunDetail("run-123");

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        RunDetailDto dto = response.getBody();
        assertEquals(2, dto.tools().size());
        
        // Verify first tool
        ToolCallDto tool1 = dto.tools().get(0);
        assertEquals("tc-1", tool1.toolCallId());
        assertEquals("kb.search", tool1.toolName());
        assertEquals(1, tool1.attempt());
        assertTrue(tool1.ok());
        assertEquals(200L, tool1.durationMs());
        assertEquals("{\"query\":\"test\"}", tool1.argsPreview());
        assertEquals("{\"results\":[]}", tool1.resultPreview());
        assertFalse(tool1.cacheHit());
        assertEquals("LIVE", tool1.freshness());
        assertEquals("{\"relevance\":0.95,\"sources\":[\"doc-1\",\"doc-2\"]}", tool1.keyInfoJson());
        assertNotNull(tool1.sourceTs());
        
        // Verify second tool
        ToolCallDto tool2 = dto.tools().get(1);
        assertEquals("tc-2", tool2.toolCallId());
        assertEquals("reasoning.analyze", tool2.toolName());
        assertEquals(2, tool2.attempt());
        assertTrue(tool2.ok());
        assertEquals(500L, tool2.durationMs());
        assertTrue(tool2.cacheHit());
        assertEquals("CACHED", tool2.freshness());
        assertEquals("{\"confidence\":0.87,\"keyPoints\":[\"point1\",\"point2\"]}", tool2.keyInfoJson());
    }

    @Test
    void getRunDetail_handlesToolWithNullKeyInfoJson() {
        // Given
        KnowledgeRunEntity run = new KnowledgeRunEntity();
        run.setId("run-123");
        run.setTraceId("trace-456");
        run.setSessionId("session-789");
        run.setMode("FAST");
        run.setModel("deepseek");
        run.setStatus("DONE");
        run.setTotalLatencyMs(1000L);
        run.setKbHitCount(3);
        run.setQuestion("Test");

        KnowledgeToolCallEntity toolCall = new KnowledgeToolCallEntity();
        toolCall.setId("tc-1");
        toolCall.setToolName("kb.search");
        toolCall.setAttempt(1);
        toolCall.setOk(true);
        toolCall.setDurationMs(100L);
        toolCall.setKeyInfoJson(null);  // No key info
        toolCall.setCalledAt(Instant.now());

        when(runRepo.findById("run-123")).thenReturn(Optional.of(run));
        when(toolCallRepo.findByRunIdOrderByCreatedAt("run-123"))
            .thenReturn(List.of(toolCall));

        // When
        ResponseEntity<RunDetailDto> response = controller.getRunDetail("run-123");

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        RunDetailDto dto = response.getBody();
        assertEquals(1, dto.tools().size());
        
        ToolCallDto tool = dto.tools().get(0);
        assertNull(tool.keyInfoJson());
    }

    @Test
    void getRunDetail_returnsNotFoundForNonexistentRun() {
        // Given
        when(runRepo.findById("nonexistent")).thenReturn(Optional.empty());

        // When
        ResponseEntity<RunDetailDto> response = controller.getRunDetail("nonexistent");

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
        verify(toolCallRepo, never()).findByRunIdOrderByCreatedAt(any());
    }

    @Test
    void getRunDetail_withCompleteHistoryData() {
        // Given
        KnowledgeRunEntity run = new KnowledgeRunEntity();
        run.setId("run-123");
        run.setTraceId("trace-456");
        run.setSessionId("session-789");
        run.setMode("FAST");
        run.setModel("deepseek");
        run.setStatus("DONE");
        run.setTotalLatencyMs(2000L);
        run.setKbHitCount(7);
        run.setKbDocIds("doc-1,doc-2,doc-3,doc-4,doc-5,doc-6,doc-7");
        run.setHistoryCount(10);
        run.setRecentQuestionsJson("[\"Question 1\",\"Question 2\",\"Question 3\",\"Question 4\",\"Question 5\"]");
        run.setQuestion("What is the comprehensive answer?");

        when(runRepo.findById("run-123")).thenReturn(Optional.of(run));
        when(toolCallRepo.findByRunIdOrderByCreatedAt("run-123")).thenReturn(List.of());

        // When
        ResponseEntity<RunDetailDto> response = controller.getRunDetail("run-123");

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        RunDetailDto dto = response.getBody();
        assertEquals(7, dto.kbHitCount());
        assertEquals("doc-1,doc-2,doc-3,doc-4,doc-5,doc-6,doc-7", dto.kbDocIds());
        assertEquals(10, dto.historyCount());
        assertTrue(dto.recentQuestionsJson().contains("Question 1"));
        assertTrue(dto.recentQuestionsJson().contains("Question 5"));
    }

    @Test
    void getToolCalls_returnsToolsWithKeyInfoJson() {
        // Given
        KnowledgeToolCallEntity toolCall = new KnowledgeToolCallEntity();
        toolCall.setId("tc-1");
        toolCall.setToolName("kb.search");
        toolCall.setAttempt(1);
        toolCall.setOk(true);
        toolCall.setDurationMs(250L);
        toolCall.setArgsPreview("{\"query\":\"test query\"}");
        toolCall.setResultPreview("{\"hits\":5}");
        toolCall.setCacheHit(false);
        toolCall.setFreshness("LIVE");
        toolCall.setKeyInfoJson("{\"topResult\":\"doc-1\",\"totalHits\":5}");
        toolCall.setCalledAt(Instant.parse("2026-02-25T12:00:00Z"));

        when(toolCallRepo.findByRunIdOrderByCreatedAt("run-123"))
            .thenReturn(List.of(toolCall));

        // When
        ResponseEntity<List<ToolCallDto>> response = controller.getToolCalls("run-123");

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        
        ToolCallDto dto = response.getBody().get(0);
        assertEquals("tc-1", dto.toolCallId());
        assertEquals("kb.search", dto.toolName());
        assertEquals("{\"topResult\":\"doc-1\",\"totalHits\":5}", dto.keyInfoJson());
    }
}
