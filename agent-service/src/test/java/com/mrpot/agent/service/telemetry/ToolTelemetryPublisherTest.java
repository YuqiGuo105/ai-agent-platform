package com.mrpot.agent.service.telemetry;

import com.mrpot.agent.common.telemetry.ToolTelemetryData;
import com.mrpot.agent.common.telemetry.ToolTelemetryEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ToolTelemetryPublisherTest {

    private RabbitTemplate rabbitTemplate;
    private ToolTelemetryPublisher publisher;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        publisher = new ToolTelemetryPublisher(rabbitTemplate);
    }

    @Test
    void publish_sends_to_correct_exchange_and_routing_key() {
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
                        .toolName("kb.search")
                        .argsDigest("abc123")
                        .build()
        );

        publisher.publish(event);

        ArgumentCaptor<String> exchangeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);

        verify(rabbitTemplate).convertAndSend(
                exchangeCaptor.capture(),
                routingKeyCaptor.capture(),
                eventCaptor.capture()
        );

        assertEquals(TelemetryAmqpConfig.EXCHANGE, exchangeCaptor.getValue());
        assertEquals("telemetry.tool.start", routingKeyCaptor.getValue());
        ToolTelemetryEvent captured = (ToolTelemetryEvent) eventCaptor.getValue();
        assertEquals("tc-123", captured.toolCallId());
    }

    @Test
    void publish_uses_correct_routing_key_for_end_event() {
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
                        .toolName("redis.get")
                        .durationMs(150L)
                        .build()
        );

        publisher.publish(event);

        ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate).convertAndSend(eq(TelemetryAmqpConfig.EXCHANGE), routingKeyCaptor.capture(), any(Object.class));

        assertEquals("telemetry.tool.end", routingKeyCaptor.getValue());
    }

    @Test
    void publish_uses_correct_routing_key_for_error_event() {
        ToolTelemetryEvent event = new ToolTelemetryEvent(
                "1",
                "tool.error",
                "tc-123",
                "run-456",
                "trace-789",
                "session-1",
                "user-1",
                Instant.now(),
                ToolTelemetryData.builder()
                        .toolName("file.extract")
                        .errorCode("TIMEOUT")
                        .errorMessage("Request timed out")
                        .build()
        );

        publisher.publish(event);

        ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate).convertAndSend(eq(TelemetryAmqpConfig.EXCHANGE), routingKeyCaptor.capture(), any(Object.class));

        assertEquals("telemetry.tool.error", routingKeyCaptor.getValue());
    }

    @Test
    void publish_handles_null_type_gracefully() {
        ToolTelemetryEvent event = new ToolTelemetryEvent(
                "1",
                null,
                "tc-123",
                "run-456",
                "trace-789",
                "session-1",
                "user-1",
                Instant.now(),
                null
        );

        // Should not throw
        assertDoesNotThrow(() -> publisher.publish(event));

        ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate).convertAndSend(eq(TelemetryAmqpConfig.EXCHANGE), routingKeyCaptor.capture(), any(Object.class));

        assertEquals("telemetry.tool.unknown", routingKeyCaptor.getValue());
    }

    @Test
    void publish_catches_exceptions_without_propagating() {
        doThrow(new RuntimeException("RabbitMQ connection failed"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        ToolTelemetryEvent event = new ToolTelemetryEvent(
                "1",
                "tool.start",
                "tc-123",
                "run-456",
                "trace-789",
                "session-1",
                "user-1",
                Instant.now(),
                null
        );

        // Should not throw, even when RabbitMQ fails
        assertDoesNotThrow(() -> publisher.publish(event));
    }

    @Test
    void publishAsync_executes_in_separate_thread() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Thread mainThread = Thread.currentThread();
        ThreadLocal<Boolean> differentThread = new ThreadLocal<>();
        
        doAnswer(invocation -> {
            differentThread.set(!Thread.currentThread().equals(mainThread));
            latch.countDown();
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        ToolTelemetryEvent event = new ToolTelemetryEvent(
                "1",
                "tool.start",
                "tc-123",
                "run-456",
                "trace-789",
                "session-1",
                "user-1",
                Instant.now(),
                null
        );

        publisher.publishAsync(event);

        // Wait for async execution
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void publishAsync_does_not_block_caller() {
        // Simulate slow RabbitMQ
        doAnswer(invocation -> {
            Thread.sleep(1000);
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        ToolTelemetryEvent event = new ToolTelemetryEvent(
                "1",
                "tool.start",
                "tc-123",
                "run-456",
                "trace-789",
                "session-1",
                "user-1",
                Instant.now(),
                null
        );

        long start = System.currentTimeMillis();
        publisher.publishAsync(event);
        long elapsed = System.currentTimeMillis() - start;

        // Should return immediately (much less than 1000ms)
        assertTrue(elapsed < 100, "publishAsync should not block: took " + elapsed + "ms");
    }

    @Test
    void publishAsync_handles_exceptions_silently() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        
        doAnswer(invocation -> {
            latch.countDown();
            throw new RuntimeException("RabbitMQ error");
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        ToolTelemetryEvent event = new ToolTelemetryEvent(
                "1",
                "tool.start",
                "tc-123",
                "run-456",
                "trace-789",
                "session-1",
                "user-1",
                Instant.now(),
                null
        );

        // Should not throw
        assertDoesNotThrow(() -> publisher.publishAsync(event));
        
        // Wait for async to complete
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }
}
