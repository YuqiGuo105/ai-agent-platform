package com.mrpot.agent.service.telemetry;

import com.mrpot.agent.common.telemetry.RunLogEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RunLogPublisherTest {

    private RabbitTemplate rabbitTemplate;
    private RunLogPublisher publisher;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        publisher = new RunLogPublisher(rabbitTemplate);
    }

    @Test
    void publish_sends_to_correct_exchange_and_routing_key_for_run_start() {
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

        publisher.publish(envelope);

        ArgumentCaptor<String> exchangeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RunLogEnvelope> envelopeCaptor = ArgumentCaptor.forClass(RunLogEnvelope.class);

        verify(rabbitTemplate).convertAndSend(
                exchangeCaptor.capture(),
                routingKeyCaptor.capture(),
                envelopeCaptor.capture()
        );

        assertEquals(TelemetryAmqpConfig.EXCHANGE, exchangeCaptor.getValue());
        assertEquals("telemetry.run.start", routingKeyCaptor.getValue());
        assertEquals(envelope, envelopeCaptor.getValue());
    }

    @Test
    void publish_sends_correct_routing_key_for_run_final() {
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
                Map.of("answerFinal", "The answer is 42", "totalLatencyMs", 1500L)
        );

        publisher.publish(envelope);

        verify(rabbitTemplate).convertAndSend(
                eq(TelemetryAmqpConfig.EXCHANGE),
                eq("telemetry.run.final"),
                eq(envelope)
        );
    }

    @Test
    void publish_sends_correct_routing_key_for_run_rag_done() {
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
                Map.of("kbHitCount", 5, "kbLatencyMs", 200L)
        );

        publisher.publish(envelope);

        verify(rabbitTemplate).convertAndSend(
                eq(TelemetryAmqpConfig.EXCHANGE),
                eq("telemetry.run.rag_done"),
                eq(envelope)
        );
    }

    @Test
    void publish_sends_correct_routing_key_for_run_failed() {
        RunLogEnvelope envelope = new RunLogEnvelope(
                "1",
                "run.failed",
                "run-123",
                "trace-456",
                "session-789",
                "user-001",
                "GENERAL",
                "deepseek",
                Instant.now(),
                Map.of("errorCode", "TimeoutException")
        );

        publisher.publish(envelope);

        verify(rabbitTemplate).convertAndSend(
                eq(TelemetryAmqpConfig.EXCHANGE),
                eq("telemetry.run.failed"),
                eq(envelope)
        );
    }

    @Test
    void publish_handles_null_type_gracefully() {
        RunLogEnvelope envelope = new RunLogEnvelope(
                "1",
                null,
                "run-123",
                "trace-456",
                "session-789",
                "user-001",
                "GENERAL",
                "deepseek",
                Instant.now(),
                Map.of()
        );

        publisher.publish(envelope);

        verify(rabbitTemplate).convertAndSend(
                eq(TelemetryAmqpConfig.EXCHANGE),
                eq("telemetry.run.unknown"),
                eq(envelope)
        );
    }

    @Test
    void publish_catches_exception_and_does_not_throw() {
        doThrow(new RuntimeException("RabbitMQ connection failed"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(RunLogEnvelope.class));

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
                Map.of("question", "Test")
        );

        // Should not throw
        assertDoesNotThrow(() -> publisher.publish(envelope));
    }
}
