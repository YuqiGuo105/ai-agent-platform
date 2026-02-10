package com.mrpot.agent.telemetry.consumer;

import com.mrpot.agent.common.telemetry.RunLogEnvelope;
import com.mrpot.agent.telemetry.entity.KnowledgeRunEntity;
import com.mrpot.agent.telemetry.repository.KnowledgeRunJpaRepository;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RunLogConsumerTest {

    private KnowledgeRunJpaRepository repo;
    private RunLogConsumer consumer;
    private Channel channel;
    private Message message;
    private MessageProperties messageProperties;

    @BeforeEach
    void setUp() {
        repo = mock(KnowledgeRunJpaRepository.class);
        consumer = new RunLogConsumer(repo);
        channel = mock(Channel.class);
        message = mock(Message.class);
        messageProperties = mock(MessageProperties.class);
        
        when(message.getMessageProperties()).thenReturn(messageProperties);
        when(messageProperties.getDeliveryTag()).thenReturn(1L);
    }

    @Test
    void onMessage_run_start_creates_new_entity() throws Exception {
        String runId = "run-123";
        RunLogEnvelope envelope = new RunLogEnvelope(
                "1",
                "run.start",
                runId,
                "trace-456",
                "session-789",
                "user-001",
                "GENERAL",
                "deepseek",
                Instant.now(),
                Map.of("question", "What is machine learning?")
        );

        when(repo.findById(runId)).thenReturn(Optional.empty());

        consumer.onMessage(envelope, message, channel);

        ArgumentCaptor<KnowledgeRunEntity> entityCaptor = ArgumentCaptor.forClass(KnowledgeRunEntity.class);
        verify(repo).save(entityCaptor.capture());
        verify(channel).basicAck(1L, false);

        KnowledgeRunEntity saved = entityCaptor.getValue();
        assertEquals(runId, saved.getId());
        assertEquals("trace-456", saved.getTraceId());
        assertEquals("session-789", saved.getSessionId());
        assertEquals("user-001", saved.getUserId());
        assertEquals("GENERAL", saved.getMode());
        assertEquals("deepseek", saved.getModel());
        assertEquals("What is machine learning?", saved.getQuestion());
        assertEquals("RUNNING", saved.getStatus());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void onMessage_run_start_updates_existing_entity() throws Exception {
        String runId = "run-123";
        KnowledgeRunEntity existing = new KnowledgeRunEntity();
        existing.setId(runId);
        existing.setCreatedAt(Instant.now().minusSeconds(60));

        RunLogEnvelope envelope = new RunLogEnvelope(
                "1",
                "run.start",
                runId,
                "trace-456",
                "session-789",
                "user-001",
                "GENERAL",
                "deepseek",
                Instant.now(),
                Map.of("question", "Updated question")
        );

        when(repo.findById(runId)).thenReturn(Optional.of(existing));

        consumer.onMessage(envelope, message, channel);

        ArgumentCaptor<KnowledgeRunEntity> entityCaptor = ArgumentCaptor.forClass(KnowledgeRunEntity.class);
        verify(repo).save(entityCaptor.capture());

        KnowledgeRunEntity saved = entityCaptor.getValue();
        assertEquals("Updated question", saved.getQuestion());
        assertEquals("RUNNING", saved.getStatus());
    }

    @Test
    void onMessage_run_rag_done_updates_kb_fields() throws Exception {
        String runId = "run-123";
        KnowledgeRunEntity existing = new KnowledgeRunEntity();
        existing.setId(runId);
        existing.setStatus("RUNNING");

        RunLogEnvelope envelope = new RunLogEnvelope(
                "1",
                "run.rag_done",
                runId,
                "trace-456",
                "session-789",
                "user-001",
                "GENERAL",
                "deepseek",
                Instant.now(),
                Map.of(
                        "kbHitCount", 3,
                        "kbLatencyMs", 150L,
                        "kbDocIds", List.of("doc1", "doc2", "doc3")
                )
        );

        when(repo.findById(runId)).thenReturn(Optional.of(existing));

        consumer.onMessage(envelope, message, channel);

        ArgumentCaptor<KnowledgeRunEntity> entityCaptor = ArgumentCaptor.forClass(KnowledgeRunEntity.class);
        verify(repo).save(entityCaptor.capture());
        verify(channel).basicAck(1L, false);

        KnowledgeRunEntity saved = entityCaptor.getValue();
        assertEquals(3, saved.getKbHitCount());
        assertEquals(150L, saved.getKbLatencyMs());
        assertEquals("doc1,doc2,doc3", saved.getKbDocIds());
    }

    @Test
    void onMessage_run_final_updates_answer_and_status() throws Exception {
        String runId = "run-123";
        KnowledgeRunEntity existing = new KnowledgeRunEntity();
        existing.setId(runId);
        existing.setStatus("RUNNING");

        RunLogEnvelope envelope = new RunLogEnvelope(
                "1",
                "run.final",
                runId,
                "trace-456",
                "session-789",
                "user-001",
                "GENERAL",
                "deepseek",
                Instant.now(),
                Map.of(
                        "answerFinal", "Machine learning is a subset of AI...",
                        "totalLatencyMs", 2500L
                )
        );

        when(repo.findById(runId)).thenReturn(Optional.of(existing));

        consumer.onMessage(envelope, message, channel);

        ArgumentCaptor<KnowledgeRunEntity> entityCaptor = ArgumentCaptor.forClass(KnowledgeRunEntity.class);
        verify(repo).save(entityCaptor.capture());
        verify(channel).basicAck(1L, false);

        KnowledgeRunEntity saved = entityCaptor.getValue();
        assertEquals("Machine learning is a subset of AI...", saved.getAnswerFinal());
        assertEquals(2500L, saved.getTotalLatencyMs());
        assertEquals("DONE", saved.getStatus());
    }

    @Test
    void onMessage_run_failed_updates_error_status() throws Exception {
        String runId = "run-123";
        KnowledgeRunEntity existing = new KnowledgeRunEntity();
        existing.setId(runId);
        existing.setStatus("RUNNING");

        RunLogEnvelope envelope = new RunLogEnvelope(
                "1",
                "run.failed",
                runId,
                "trace-456",
                "session-789",
                "user-001",
                "GENERAL",
                "deepseek",
                Instant.now(),
                Map.of("errorCode", "TimeoutException")
        );

        when(repo.findById(runId)).thenReturn(Optional.of(existing));

        consumer.onMessage(envelope, message, channel);

        ArgumentCaptor<KnowledgeRunEntity> entityCaptor = ArgumentCaptor.forClass(KnowledgeRunEntity.class);
        verify(repo).save(entityCaptor.capture());
        verify(channel).basicAck(1L, false);

        KnowledgeRunEntity saved = entityCaptor.getValue();
        assertEquals("FAILED", saved.getStatus());
        assertEquals("TimeoutException", saved.getErrorCode());
    }

    @Test
    void onMessage_run_cancelled_updates_status() throws Exception {
        String runId = "run-123";
        KnowledgeRunEntity existing = new KnowledgeRunEntity();
        existing.setId(runId);
        existing.setStatus("RUNNING");

        RunLogEnvelope envelope = new RunLogEnvelope(
                "1",
                "run.cancelled",
                runId,
                "trace-456",
                "session-789",
                "user-001",
                "GENERAL",
                "deepseek",
                Instant.now(),
                Map.of()
        );

        when(repo.findById(runId)).thenReturn(Optional.of(existing));

        consumer.onMessage(envelope, message, channel);

        ArgumentCaptor<KnowledgeRunEntity> entityCaptor = ArgumentCaptor.forClass(KnowledgeRunEntity.class);
        verify(repo).save(entityCaptor.capture());
        verify(channel).basicAck(1L, false);

        KnowledgeRunEntity saved = entityCaptor.getValue();
        assertEquals("CANCELLED", saved.getStatus());
    }

    @Test
    void onMessage_unknown_type_is_ignored() throws Exception {
        RunLogEnvelope envelope = new RunLogEnvelope(
                "1",
                "run.unknown_type",
                "run-123",
                "trace-456",
                "session-789",
                "user-001",
                "GENERAL",
                "deepseek",
                Instant.now(),
                Map.of()
        );

        consumer.onMessage(envelope, message, channel);

        verify(repo, never()).save(any());
        verify(channel).basicAck(1L, false);
    }

    @Test
    void onMessage_nacks_on_exception() throws Exception {
        String runId = "run-123";
        RunLogEnvelope envelope = new RunLogEnvelope(
                "1",
                "run.start",
                runId,
                "trace-456",
                "session-789",
                "user-001",
                "GENERAL",
                "deepseek",
                Instant.now(),
                Map.of("question", "Test")
        );

        when(repo.findById(runId)).thenReturn(Optional.empty());
        doThrow(new RuntimeException("Database error")).when(repo).save(any());

        consumer.onMessage(envelope, message, channel);

        verify(channel).basicNack(1L, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void onMessage_handles_non_existent_run_for_final() throws Exception {
        String runId = "run-not-found";
        RunLogEnvelope envelope = new RunLogEnvelope(
                "1",
                "run.final",
                runId,
                "trace-456",
                "session-789",
                "user-001",
                "GENERAL",
                "deepseek",
                Instant.now(),
                Map.of("answerFinal", "Some answer")
        );

        when(repo.findById(runId)).thenReturn(Optional.empty());

        consumer.onMessage(envelope, message, channel);

        // Should not save anything if entity doesn't exist
        verify(repo, never()).save(any());
        verify(channel).basicAck(1L, false);
    }

    @Test
    void onMessage_truncates_long_question() throws Exception {
        String runId = "run-123";
        String longQuestion = "a".repeat(5000); // Longer than 3800 char limit

        RunLogEnvelope envelope = new RunLogEnvelope(
                "1",
                "run.start",
                runId,
                "trace-456",
                "session-789",
                "user-001",
                "GENERAL",
                "deepseek",
                Instant.now(),
                Map.of("question", longQuestion)
        );

        when(repo.findById(runId)).thenReturn(Optional.empty());

        consumer.onMessage(envelope, message, channel);

        ArgumentCaptor<KnowledgeRunEntity> entityCaptor = ArgumentCaptor.forClass(KnowledgeRunEntity.class);
        verify(repo).save(entityCaptor.capture());

        KnowledgeRunEntity saved = entityCaptor.getValue();
        assertTrue(saved.getQuestion().length() < 5000);
        assertTrue(saved.getQuestion().endsWith(" ...[truncated]"));
    }
}
