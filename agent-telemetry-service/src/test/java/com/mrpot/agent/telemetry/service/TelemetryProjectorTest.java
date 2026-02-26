package com.mrpot.agent.telemetry.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mrpot.agent.common.telemetry.RunLogEnvelope;
import com.mrpot.agent.telemetry.entity.EsOutboxEntity;
import com.mrpot.agent.telemetry.entity.KnowledgeRunEntity;
import com.mrpot.agent.telemetry.entity.KnowledgeRunEventEntity;
import com.mrpot.agent.telemetry.repository.EsOutboxJpaRepository;
import com.mrpot.agent.telemetry.repository.KnowledgeRunEventJpaRepository;
import com.mrpot.agent.telemetry.repository.KnowledgeRunJpaRepository;
import com.mrpot.agent.telemetry.repository.KnowledgeToolCallJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TelemetryProjector, focusing on run event processing
 * including the new processHistoryDone method.
 */
class TelemetryProjectorTest {

    private KnowledgeRunJpaRepository runRepo;
    private KnowledgeToolCallJpaRepository toolCallRepo;
    private KnowledgeRunEventJpaRepository eventRepo;
    private EsOutboxJpaRepository outboxRepo;
    private ObjectMapper objectMapper;
    private TelemetryProjector projector;

    @BeforeEach
    void setUp() {
        runRepo = mock(KnowledgeRunJpaRepository.class);
        toolCallRepo = mock(KnowledgeToolCallJpaRepository.class);
        eventRepo = mock(KnowledgeRunEventJpaRepository.class);
        outboxRepo = mock(EsOutboxJpaRepository.class);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        projector = new TelemetryProjector(runRepo, toolCallRepo, eventRepo, outboxRepo, objectMapper);
        
        // Mock event recording to return true (first time processing)
        when(eventRepo.existsByEventId(anyString())).thenReturn(false);
    }

    @Test
    void processHistoryDone_setsHistoryCountAndRecentQuestions() {
        // Given
        KnowledgeRunEntity existingRun = new KnowledgeRunEntity();
        existingRun.setId("run-123");
        existingRun.setStatus("RUNNING");
        existingRun.setCreatedAt(Instant.now());
        
        List<String> recentQuestions = List.of(
            "What is machine learning?",
            "Explain deep learning",
            "What is neural network?"
        );
        
        RunLogEnvelope envelope = new RunLogEnvelope(
            "1",
            "run.history_done",
            "run-123",
            "trace-456",
            "session-789",
            "user-001",
            "GENERAL",
            "deepseek",
            Instant.now(),
            Map.of(
                "historyCount", 6,
                "recentQuestions", recentQuestions
            )
        );

        when(runRepo.findById("run-123")).thenReturn(Optional.of(existingRun));

        // When
        boolean processed = projector.processRunEvent(envelope);

        // Then
        assertTrue(processed);
        
        ArgumentCaptor<KnowledgeRunEntity> runCaptor = ArgumentCaptor.forClass(KnowledgeRunEntity.class);
        verify(runRepo).save(runCaptor.capture());
        
        KnowledgeRunEntity savedRun = runCaptor.getValue();
        assertEquals(6, savedRun.getHistoryCount());
        assertNotNull(savedRun.getRecentQuestionsJson());
        
        // Verify JSON serialization
        assertTrue(savedRun.getRecentQuestionsJson().contains("machine learning"));
        assertTrue(savedRun.getRecentQuestionsJson().contains("deep learning"));
        
        // Verify ES outbox entry
        ArgumentCaptor<EsOutboxEntity> outboxCaptor = ArgumentCaptor.forClass(EsOutboxEntity.class);
        verify(outboxRepo).save(outboxCaptor.capture());
        
        EsOutboxEntity outbox = outboxCaptor.getValue();
        assertEquals(TelemetryProjector.ES_INDEX_RUNS, outbox.getIndexName());
        assertEquals("run-123", outbox.getDocId());
    }

    @Test
    void processHistoryDone_handlesNullRecentQuestions() {
        // Given
        KnowledgeRunEntity existingRun = new KnowledgeRunEntity();
        existingRun.setId("run-123");
        existingRun.setStatus("RUNNING");
        existingRun.setCreatedAt(Instant.now());
        
        RunLogEnvelope envelope = new RunLogEnvelope(
            "1",
            "run.history_done",
            "run-123",
            "trace-456",
            "session-789",
            "user-001",
            "GENERAL",
            "deepseek",
            Instant.now(),
            Map.of("historyCount", 0)
        );

        when(runRepo.findById("run-123")).thenReturn(Optional.of(existingRun));

        // When
        boolean processed = projector.processRunEvent(envelope);

        // Then
        assertTrue(processed);
        
        ArgumentCaptor<KnowledgeRunEntity> runCaptor = ArgumentCaptor.forClass(KnowledgeRunEntity.class);
        verify(runRepo).save(runCaptor.capture());
        
        KnowledgeRunEntity savedRun = runCaptor.getValue();
        assertEquals(0, savedRun.getHistoryCount());
        assertNull(savedRun.getRecentQuestionsJson());
    }

    @Test
    void processHistoryDone_handlesEmptyRecentQuestions() {
        // Given
        KnowledgeRunEntity existingRun = new KnowledgeRunEntity();
        existingRun.setId("run-123");
        existingRun.setStatus("RUNNING");
        existingRun.setCreatedAt(Instant.now());
        
        RunLogEnvelope envelope = new RunLogEnvelope(
            "1",
            "run.history_done",
            "run-123",
            "trace-456",
            "session-789",
            "user-001",
            "GENERAL",
            "deepseek",
            Instant.now(),
            Map.of(
                "historyCount", 4,
                "recentQuestions", List.of()
            )
        );

        when(runRepo.findById("run-123")).thenReturn(Optional.of(existingRun));

        // When
        boolean processed = projector.processRunEvent(envelope);

        // Then
        assertTrue(processed);
        
        ArgumentCaptor<KnowledgeRunEntity> runCaptor = ArgumentCaptor.forClass(KnowledgeRunEntity.class);
        verify(runRepo).save(runCaptor.capture());
        
        KnowledgeRunEntity savedRun = runCaptor.getValue();
        assertEquals(4, savedRun.getHistoryCount());
        assertEquals("[]", savedRun.getRecentQuestionsJson());
    }

    @Test
    void processHistoryDone_skipsIfRunNotFound() {
        // Given
        RunLogEnvelope envelope = new RunLogEnvelope(
            "1",
            "run.history_done",
            "run-nonexistent",
            "trace-456",
            "session-789",
            "user-001",
            "GENERAL",
            "deepseek",
            Instant.now(),
            Map.of("historyCount", 5)
        );

        when(runRepo.findById("run-nonexistent")).thenReturn(Optional.empty());

        // When
        boolean processed = projector.processRunEvent(envelope);

        // Then
        assertTrue(processed);
        verify(runRepo, never()).save(any());
        verify(outboxRepo, never()).save(any());
    }

    @Test
    void processRagDone_setsKbDocIds() {
        // Given
        KnowledgeRunEntity existingRun = new KnowledgeRunEntity();
        existingRun.setId("run-123");
        existingRun.setStatus("RUNNING");
        existingRun.setCreatedAt(Instant.now());
        
        RunLogEnvelope envelope = new RunLogEnvelope(
            "1",
            "run.rag_done",
            "run-123",
            "trace-456",
            "session-789",
            "user-001",
            "GENERAL",
            "deepseek",
            Instant.now(),
            Map.of(
                "kbHitCount", 5,
                "kbLatencyMs", 250L,
                "kbDocIds", List.of("doc-1", "doc-2", "doc-3")
            )
        );

        when(runRepo.findById("run-123")).thenReturn(Optional.of(existingRun));

        // When
        boolean processed = projector.processRunEvent(envelope);

        // Then
        assertTrue(processed);
        
        ArgumentCaptor<KnowledgeRunEntity> runCaptor = ArgumentCaptor.forClass(KnowledgeRunEntity.class);
        verify(runRepo).save(runCaptor.capture());
        
        KnowledgeRunEntity savedRun = runCaptor.getValue();
        assertEquals(5, savedRun.getKbHitCount());
        assertEquals(250L, savedRun.getKbLatencyMs());
        assertEquals("doc-1,doc-2,doc-3", savedRun.getKbDocIds());
    }

    @Test
    void processDuplicateEvent_returnsFlaseAndSkipsProcessing() {
        // Given - Simulate constraint violation when trying to save duplicate event
        when(eventRepo.save(any(KnowledgeRunEventEntity.class)))
            .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate key"));
        
        RunLogEnvelope envelope = new RunLogEnvelope(
            "1",
            "run.history_done",
            "run-123",
            "trace-456",
            "session-789",
            "user-001",
            "GENERAL",
            "deepseek",
            Instant.now(),
            Map.of("historyCount", 5)
        );

        // When
        boolean processed = projector.processRunEvent(envelope);

        // Then
        assertFalse(processed);
        verify(eventRepo, times(1)).save(any(KnowledgeRunEventEntity.class));
        verify(runRepo, never()).findById(any());
        verify(runRepo, never()).save(any());
    }

    @Test
    void processRunStart_createsNewRun() {
        // Given
        RunLogEnvelope envelope = new RunLogEnvelope(
            "1",
            "run.start",
            "run-123",
            "trace-456",
            "session-789",
            "user-001",
            "GENERAL",
            "deepseek",
            Instant.now(),
            Map.of("question", "What is AI?")
        );

        when(runRepo.findById("run-123")).thenReturn(Optional.empty());

        // When
        boolean processed = projector.processRunEvent(envelope);

        // Then
        assertTrue(processed);
        
        ArgumentCaptor<KnowledgeRunEntity> runCaptor = ArgumentCaptor.forClass(KnowledgeRunEntity.class);
        verify(runRepo).save(runCaptor.capture());
        
        KnowledgeRunEntity savedRun = runCaptor.getValue();
        assertEquals("run-123", savedRun.getId());
        assertEquals("trace-456", savedRun.getTraceId());
        assertEquals("RUNNING", savedRun.getStatus());
        assertEquals("What is AI?", savedRun.getQuestion());
    }
}
