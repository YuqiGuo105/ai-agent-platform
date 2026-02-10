package com.mrpot.agent.telemetry.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mrpot.agent.common.telemetry.RunLogEnvelope;
import com.mrpot.agent.common.telemetry.ToolTelemetryData;
import com.mrpot.agent.common.telemetry.ToolTelemetryEvent;
import com.mrpot.agent.telemetry.entity.KnowledgeRunEntity;
import com.mrpot.agent.telemetry.entity.KnowledgeToolCallEntity;
import com.mrpot.agent.telemetry.repository.*;
import com.mrpot.agent.telemetry.service.TelemetryProjector;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RunLogConsumerTest {

    private KnowledgeRunJpaRepository runRepo;
    private KnowledgeToolCallJpaRepository toolCallRepo;
    private KnowledgeRunEventJpaRepository eventRepo;
    private EsOutboxJpaRepository outboxRepo;
    private ObjectMapper objectMapper;
    private TelemetryProjector projector;
    private RunLogConsumer consumer;
    private Channel channel;
    private MessageProperties messageProperties;

    @BeforeEach
    void setUp() {
        runRepo = mock(KnowledgeRunJpaRepository.class);
        toolCallRepo = mock(KnowledgeToolCallJpaRepository.class);
        eventRepo = mock(KnowledgeRunEventJpaRepository.class);
        outboxRepo = mock(EsOutboxJpaRepository.class);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        // Create real projector with mocked repositories
        projector = new TelemetryProjector(runRepo, toolCallRepo, eventRepo, outboxRepo, objectMapper);
        consumer = new RunLogConsumer(projector, objectMapper);
        channel = mock(Channel.class);
        messageProperties = mock(MessageProperties.class);
        
        when(messageProperties.getDeliveryTag()).thenReturn(1L);
    }

    private Message createMessage(String jsonBody) {
        Message msg = mock(Message.class);
        when(msg.getMessageProperties()).thenReturn(messageProperties);
        when(msg.getBody()).thenReturn(jsonBody.getBytes());
        return msg;
    }

    @Test
    void onMessage_run_start_creates_entity() throws Exception {
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
                Map.of("question", "What is machine learning?")
        );
        
        String json = objectMapper.writeValueAsString(envelope);
        Message message = createMessage(json);

        when(runRepo.findById("run-123")).thenReturn(Optional.empty());
        when(eventRepo.existsByEventId(anyString())).thenReturn(false);

        consumer.onMessage(message, channel);

        ArgumentCaptor<KnowledgeRunEntity> captor = ArgumentCaptor.forClass(KnowledgeRunEntity.class);
        verify(runRepo).save(captor.capture());
        verify(channel).basicAck(1L, false);

        KnowledgeRunEntity saved = captor.getValue();
        assertEquals("run-123", saved.getId());
        assertEquals("trace-456", saved.getTraceId());
        assertEquals("RUNNING", saved.getStatus());
    }

    @Test
    void onMessage_run_final_updates_status() throws Exception {
        KnowledgeRunEntity existing = new KnowledgeRunEntity();
        existing.setId("run-123");
        existing.setStatus("RUNNING");
        existing.setCreatedAt(Instant.now());
        
        RunLogEnvelope envelope = new RunLogEnvelope(
                "1",
                "run.final",
                "run-123",
                "trace-456",
                "session-789",
                "user-001",
                "GENERAL",
                "deepseek",
                Instant.now(),
                Map.of("answerFinal", "Machine learning is a subset of AI...")
        );
        
        String json = objectMapper.writeValueAsString(envelope);
        Message message = createMessage(json);

        when(runRepo.findById("run-123")).thenReturn(Optional.of(existing));
        when(eventRepo.existsByEventId(anyString())).thenReturn(false);

        consumer.onMessage(message, channel);

        ArgumentCaptor<KnowledgeRunEntity> captor = ArgumentCaptor.forClass(KnowledgeRunEntity.class);
        verify(runRepo).save(captor.capture());
        verify(channel).basicAck(1L, false);

        KnowledgeRunEntity saved = captor.getValue();
        assertEquals("DONE", saved.getStatus());
        assertEquals("Machine learning is a subset of AI...", saved.getAnswerFinal());
    }

    @Test
    void onMessage_tool_start_records_event() throws Exception {
        ToolTelemetryEvent event = new ToolTelemetryEvent(
                "1",
                "tool.start",
                "tc-123",
                "run-456",
                "trace-789",
                "session-1",
                "user-1",
                Instant.now(),
                ToolTelemetryData.builder()
                        .toolName("kb_search")
                        .argsDigest("abc123")
                        .argsPreview("{\"query\":\"test\"}")
                        .build()
        );
        
        String json = objectMapper.writeValueAsString(event);
        Message message = createMessage(json);

        when(eventRepo.existsByEventId(anyString())).thenReturn(false);

        consumer.onMessage(message, channel);

        // tool.start only records idempotency event, doesn't create entity
        verify(toolCallRepo, never()).save(any());
        verify(channel).basicAck(1L, false);
    }

    @Test
    void onMessage_tool_end_creates_entity() throws Exception {
        ToolTelemetryEvent event = new ToolTelemetryEvent(
                "1",
                "tool.end",
                "tc-123",
                "run-456",
                "trace-789",
                "session-1",
                "user-1",
                Instant.now(),
                ToolTelemetryData.builder()
                        .toolName("kb_search")
                        .argsDigest("abc123")
                        .resultDigest("def456")
                        .resultPreview("{\"results\":[]}")
                        .durationMs(150L)
                        .cacheHit(true)
                        .keyInfo(Map.of("hitCount", 5))
                        .build()
        );
        
        String json = objectMapper.writeValueAsString(event);
        Message message = createMessage(json);

        when(eventRepo.existsByEventId(anyString())).thenReturn(false);

        consumer.onMessage(message, channel);

        ArgumentCaptor<KnowledgeToolCallEntity> captor = ArgumentCaptor.forClass(KnowledgeToolCallEntity.class);
        verify(toolCallRepo).save(captor.capture());
        verify(channel).basicAck(1L, false);

        KnowledgeToolCallEntity saved = captor.getValue();
        assertEquals("tc-123", saved.getId());
        assertEquals("run-456", saved.getRunId());
        assertEquals("kb_search", saved.getToolName());
        assertEquals(150L, saved.getDurationMs());
        assertTrue(saved.getCacheHit());
        assertTrue(saved.getOk());
    }

    @Test
    void onMessage_unknown_type_acks_without_processing() throws Exception {
        String json = "{\"type\":\"unknown.type\",\"runId\":\"run-123\"}";
        Message message = createMessage(json);

        consumer.onMessage(message, channel);

        verify(runRepo, never()).save(any());
        verify(toolCallRepo, never()).save(any());
        verify(channel).basicAck(1L, false);
    }

    @Test
    void onMessage_nacks_on_parse_exception() throws Exception {
        String invalidJson = "{invalid json}";
        Message message = createMessage(invalidJson);

        consumer.onMessage(message, channel);

        verify(channel).basicNack(1L, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void onMessage_handles_empty_type() throws Exception {
        String json = "{\"runId\":\"run-123\"}";  // No type field
        Message message = createMessage(json);

        consumer.onMessage(message, channel);

        verify(runRepo, never()).save(any());
        verify(toolCallRepo, never()).save(any());
        verify(channel).basicAck(1L, false);
    }
    
    @Test
    void onMessage_idempotent_skips_duplicate_events() throws Exception {
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
                Map.of("question", "What is machine learning?")
        );
        
        String json = objectMapper.writeValueAsString(envelope);
        Message message = createMessage(json);

        // Simulate duplicate event by having eventRepo.save throw DataIntegrityViolationException
        doThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate"))
                .when(eventRepo).save(any());

        consumer.onMessage(message, channel);

        // Should not save run entity since event was duplicate
        verify(runRepo, never()).save(any());
        verify(channel).basicAck(1L, false);
    }
}
