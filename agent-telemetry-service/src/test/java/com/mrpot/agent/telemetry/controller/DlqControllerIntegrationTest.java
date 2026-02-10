package com.mrpot.agent.telemetry.controller;

import com.mrpot.agent.telemetry.entity.TelemetryDlqMessageEntity;
import com.mrpot.agent.telemetry.repository.TelemetryDlqMessageJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlGroup;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for DlqController using SQL test data.
 * RabbitTemplate is mocked since we don't want to actually publish messages.
 * 
 * Test data includes 8 DLQ messages:
 * - ID 1-3, 6: PENDING status
 * - ID 4, 7: REQUEUED status
 * - ID 5, 8: IGNORED status
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@SqlGroup({
    @Sql(scripts = "classpath:test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD),
    @Sql(scripts = "classpath:cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
})
class DlqControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TelemetryDlqMessageJpaRepository dlqRepo;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Nested
    @DisplayName("GET /api/dlq")
    class ListDlqMessagesTests {

        @Test
        @DisplayName("Should return all DLQ messages when no status filter")
        void listDlqMessages_noFilter_returnsAll() throws Exception {
            mockMvc.perform(get("/api/dlq"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)));  // 8 DLQ messages in test data
        }

        @Test
        @DisplayName("Should filter DLQ messages by PENDING status")
        void listDlqMessages_pendingFilter_returnsOnlyPending() throws Exception {
            mockMvc.perform(get("/api/dlq")
                    .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))  // IDs 101, 102, 103, 106
                .andExpect(jsonPath("$[*].status", everyItem(equalTo("PENDING"))));
        }

        @Test
        @DisplayName("Should filter DLQ messages by REQUEUED status")
        void listDlqMessages_requeuedFilter_returnsOnlyRequeued() throws Exception {
            mockMvc.perform(get("/api/dlq")
                    .param("status", "REQUEUED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))  // IDs 104, 107
                .andExpect(jsonPath("$[*].status", everyItem(equalTo("REQUEUED"))));
        }

        @Test
        @DisplayName("Should filter DLQ messages by IGNORED status")
        void listDlqMessages_ignoredFilter_returnsOnlyIgnored() throws Exception {
            mockMvc.perform(get("/api/dlq")
                    .param("status", "IGNORED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))  // IDs 105, 108
                .andExpect(jsonPath("$[*].status", everyItem(equalTo("IGNORED"))));
        }

        @Test
        @DisplayName("Should ignore blank status filter")
        void listDlqMessages_blankFilter_returnsAll() throws Exception {
            mockMvc.perform(get("/api/dlq")
                    .param("status", "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)));
        }
    }

    @Nested
    @DisplayName("GET /api/dlq/{id}")
    class GetDlqMessageTests {

        @Test
        @DisplayName("Should return DLQ message when exists")
        void getDlqMessage_exists_returnsMessage() throws Exception {
            mockMvc.perform(get("/api/dlq/{id}", 101))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.runId").value("run-dlq-001"))
                .andExpect(jsonPath("$.traceId").value("trace-dlq-001"))
                .andExpect(jsonPath("$.sessionId").value("session-dlq-001"))
                .andExpect(jsonPath("$.exchange").value("mrpot.telemetry.x"))
                .andExpect(jsonPath("$.routingKey").value("telemetry.run.start"))
                .andExpect(jsonPath("$.errorType").value("JsonParseException"));
        }

        @Test
        @DisplayName("Should return message with requeue info when already requeued")
        void getDlqMessage_requeued_returnsRequeueInfo() throws Exception {
            mockMvc.perform(get("/api/dlq/{id}", 104))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(104))
                .andExpect(jsonPath("$.status").value("REQUEUED"))
                .andExpect(jsonPath("$.requeueCount").value(2))
                .andExpect(jsonPath("$.lastRequeueAt").isNotEmpty());
        }

        @Test
        @DisplayName("Should return 404 when DLQ message not found")
        void getDlqMessage_notFound_returns404() throws Exception {
            mockMvc.perform(get("/api/dlq/{id}", 999))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return message with text payload when no JSON")
        void getDlqMessage_textPayload_returnsText() throws Exception {
            mockMvc.perform(get("/api/dlq/{id}", 105))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(105))
                .andExpect(jsonPath("$.payloadJson").isEmpty())
                .andExpect(jsonPath("$.payloadText", containsString("Invalid non-JSON message")));
        }
    }

    @Nested
    @DisplayName("POST /api/dlq/{id}/requeue")
    class RequeueSingleTests {

        @Test
        @DisplayName("Should requeue message with JSON payload")
        void requeue_jsonPayload_publishesAndUpdates() throws Exception {
            // Act
            mockMvc.perform(post("/api/dlq/{id}/requeue", 101))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.id").value(101));

            // Verify RabbitMQ publish was called
            verify(rabbitTemplate).convertAndSend(
                eq("mrpot.telemetry.x"),
                eq("telemetry.run.start"),
                anyString()
            );

            // Verify database was updated
            TelemetryDlqMessageEntity updated = dlqRepo.findById(101L).orElseThrow();
            assertEquals("REQUEUED", updated.getStatus());
            assertEquals(1, updated.getRequeueCount());
            assertNotNull(updated.getLastRequeueAt());
        }

        @Test
        @DisplayName("Should requeue message with text payload when no JSON")
        void requeue_textPayload_publishesText() throws Exception {
            // ID 105 has payloadText but no payloadJson
            mockMvc.perform(post("/api/dlq/{id}/requeue", 105))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

            verify(rabbitTemplate).convertAndSend(
                anyString(),
                anyString(),
                eq("Invalid non-JSON message: <corrupted binary data>")
            );
        }

        @Test
        @DisplayName("Should use default exchange/routing key when not set")
        void requeue_noExchangeOrKey_usesDefaults() throws Exception {
            // ID 108 has empty exchange and routing key
            mockMvc.perform(post("/api/dlq/{id}/requeue", 108))
                .andExpect(status().isOk());

            verify(rabbitTemplate).convertAndSend(
                eq("mrpot.telemetry.x"),
                eq("telemetry.run.unknown"),
                anyString()
            );
        }

        @Test
        @DisplayName("Should return 404 when message not found")
        void requeue_notFound_returns404() throws Exception {
            mockMvc.perform(post("/api/dlq/{id}/requeue", 999))
                .andExpect(status().isNotFound());

            verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
        }

        @Test
        @DisplayName("Should increment requeue count on multiple requeues")
        void requeue_alreadyRequeued_incrementsCount() throws Exception {
            // ID 104 already has requeueCount=2
            mockMvc.perform(post("/api/dlq/{id}/requeue", 104))
                .andExpect(status().isOk());

            TelemetryDlqMessageEntity updated = dlqRepo.findById(104L).orElseThrow();
            assertEquals(3, updated.getRequeueCount());
        }
    }

    @Nested
    @DisplayName("POST /api/dlq/requeue")
    class BatchRequeueTests {

        @Test
        @DisplayName("Should requeue all pending messages up to limit")
        void batchRequeue_pendingMessages_requeuesThem() throws Exception {
            mockMvc.perform(post("/api/dlq/requeue")
                    .param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requeued").value(4));  // 4 PENDING messages

            // All PENDING should now be REQUEUED
            assertEquals(0, dlqRepo.countByStatus("PENDING"));
            verify(rabbitTemplate, times(4)).convertAndSend(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should respect limit parameter")
        void batchRequeue_withLimit_stopsAtLimit() throws Exception {
            mockMvc.perform(post("/api/dlq/requeue")
                    .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requeued").value(2));

            // 2 of 4 PENDING messages should now be REQUEUED
            assertEquals(2, dlqRepo.countByStatus("PENDING"));
            verify(rabbitTemplate, times(2)).convertAndSend(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should use default limit when not specified")
        void batchRequeue_noLimit_usesDefault() throws Exception {
            mockMvc.perform(post("/api/dlq/requeue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requeued").value(4));  // Default limit is 100, we have 4 pending
        }

        @Test
        @DisplayName("Should return 0 when no pending messages after all requeued")
        void batchRequeue_noPendingAfterRequeue_returnsZero() throws Exception {
            // First, requeue all
            mockMvc.perform(post("/api/dlq/requeue"))
                .andExpect(jsonPath("$.requeued").value(4));

            // Now try again - should be 0
            mockMvc.perform(post("/api/dlq/requeue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requeued").value(0));
        }
    }

    @Nested
    @DisplayName("POST /api/dlq/{id}/ignore")
    class IgnoreTests {

        @Test
        @DisplayName("Should mark message as ignored")
        void ignore_validId_marksIgnored() throws Exception {
            mockMvc.perform(post("/api/dlq/{id}/ignore", 101))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.status").value("IGNORED"));

            // Verify in database
            TelemetryDlqMessageEntity updated = dlqRepo.findById(101L).orElseThrow();
            assertEquals("IGNORED", updated.getStatus());
        }

        @Test
        @DisplayName("Should return 404 when message not found")
        void ignore_notFound_returns404() throws Exception {
            mockMvc.perform(post("/api/dlq/{id}/ignore", 999))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Can ignore already requeued message")
        void ignore_requeuedMessage_marksIgnored() throws Exception {
            // ID 104 is REQUEUED, should be able to ignore it
            mockMvc.perform(post("/api/dlq/{id}/ignore", 104))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IGNORED"));

            TelemetryDlqMessageEntity updated = dlqRepo.findById(104L).orElseThrow();
            assertEquals("IGNORED", updated.getStatus());
        }
    }

    @Nested
    @DisplayName("GET /api/dlq/stats")
    class StatsTests {

        @Test
        @DisplayName("Should return correct statistics from test data")
        void getStats_returnsCorrectCounts() throws Exception {
            mockMvc.perform(get("/api/dlq/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(8))
                .andExpect(jsonPath("$.pending").value(4))    // IDs 101, 102, 103, 106
                .andExpect(jsonPath("$.requeued").value(2))   // IDs 104, 107
                .andExpect(jsonPath("$.ignored").value(2));   // IDs 105, 108
        }

        @Test
        @DisplayName("Should update statistics after operations")
        void getStats_afterOperations_updatesCorrectly() throws Exception {
            // Requeue one message
            mockMvc.perform(post("/api/dlq/{id}/requeue", 101));
            
            // Ignore another
            mockMvc.perform(post("/api/dlq/{id}/ignore", 102));

            // Stats should reflect changes
            mockMvc.perform(get("/api/dlq/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(8))
                .andExpect(jsonPath("$.pending").value(2))    // 4 - 2 = 2 (IDs 103, 106 remain)
                .andExpect(jsonPath("$.requeued").value(3))   // 2 + 1 = 3 (ID 101 added)
                .andExpect(jsonPath("$.ignored").value(3));   // 2 + 1 = 3 (ID 102 added)
        }
    }
}
