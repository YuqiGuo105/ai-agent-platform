package com.mrpot.agent.telemetry.repository;

import com.mrpot.agent.telemetry.entity.TelemetryDlqMessageEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository tests for TelemetryDlqMessageJpaRepository.
 * Uses @DataJpaTest for in-memory database testing with SQL test data.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "classpath:test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class TelemetryDlqMessageJpaRepositoryTest {

    @Autowired
    private TelemetryDlqMessageJpaRepository repository;

    @Test
    @DisplayName("findByStatusOrderByReceivedAtDesc - should return PENDING messages in correct order")
    void findByStatusOrderByReceivedAtDesc_shouldReturnPendingMessagesInOrder() {
        List<TelemetryDlqMessageEntity> result = repository.findByStatusOrderByReceivedAtDesc("PENDING");

        // PENDING: 4 messages (ids: 101, 102, 103, 106)
        assertThat(result).hasSize(4);
        // Should be ordered by receivedAt DESC (newest first)
        // id=106 at 09:25, id=103 at 09:10, id=102 at 09:05, id=101 at 09:00
        assertThat(result.get(0).getId()).isEqualTo(106L);
        assertThat(result.get(1).getId()).isEqualTo(103L);
        assertThat(result.get(2).getId()).isEqualTo(102L);
        assertThat(result.get(3).getId()).isEqualTo(101L);
    }

    @Test
    @DisplayName("findByStatusOrderByReceivedAtDesc - should return REQUEUED messages")
    void findByStatusOrderByReceivedAtDesc_shouldReturnRequeuedMessages() {
        List<TelemetryDlqMessageEntity> result = repository.findByStatusOrderByReceivedAtDesc("REQUEUED");

        // REQUEUED: 2 messages (ids: 104, 107)
        assertThat(result).hasSize(2);
        // id=107 at 09:30, id=104 at 09:15
        assertThat(result.get(0).getId()).isEqualTo(107L);
        assertThat(result.get(1).getId()).isEqualTo(104L);
    }

    @Test
    @DisplayName("findByStatusOrderByReceivedAtDesc - should return IGNORED messages")
    void findByStatusOrderByReceivedAtDesc_shouldReturnIgnoredMessages() {
        List<TelemetryDlqMessageEntity> result = repository.findByStatusOrderByReceivedAtDesc("IGNORED");

        // IGNORED: 2 messages (ids: 105, 108)
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("findByStatusOrderByReceivedAtDesc - should return empty for unknown status")
    void findByStatusOrderByReceivedAtDesc_shouldReturnEmptyForUnknownStatus() {
        List<TelemetryDlqMessageEntity> result = repository.findByStatusOrderByReceivedAtDesc("UNKNOWN");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByRunId - should return messages for specific run")
    void findByRunId_shouldReturnMessagesForRun() {
        List<TelemetryDlqMessageEntity> result = repository.findByRunId("run-dlq-001");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTraceId()).isEqualTo("trace-dlq-001");
        assertThat(result.get(0).getSessionId()).isEqualTo("session-dlq-001");
    }

    @Test
    @DisplayName("findByRunId - should return empty for non-existing run")
    void findByRunId_shouldReturnEmptyForNonExistingRun() {
        List<TelemetryDlqMessageEntity> result = repository.findByRunId("non-existing-run");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("countByStatus - should count PENDING messages")
    void countByStatus_shouldCountPendingMessages() {
        long count = repository.countByStatus("PENDING");

        assertThat(count).isEqualTo(4);
    }

    @Test
    @DisplayName("countByStatus - should count REQUEUED messages")
    void countByStatus_shouldCountRequeuedMessages() {
        long count = repository.countByStatus("REQUEUED");

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("countByStatus - should count IGNORED messages")
    void countByStatus_shouldCountIgnoredMessages() {
        long count = repository.countByStatus("IGNORED");

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("countByStatus - should return 0 for unknown status")
    void countByStatus_shouldReturnZeroForUnknownStatus() {
        long count = repository.countByStatus("UNKNOWN");

        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("findAll - should return all 8 messages")
    void findAll_shouldReturnAllMessages() {
        List<TelemetryDlqMessageEntity> result = repository.findAll();

        assertThat(result).hasSize(8);
    }

    @Test
    @DisplayName("findById - should return message with all fields")
    void findById_shouldReturnMessageWithAllFields() {
        Optional<TelemetryDlqMessageEntity> result = repository.findById(101L);

        assertThat(result).isPresent();
        TelemetryDlqMessageEntity msg = result.get();
        assertThat(msg.getExchange()).isEqualTo("mrpot.telemetry.x");
        assertThat(msg.getRoutingKey()).isEqualTo("telemetry.run.start");
        assertThat(msg.getErrorType()).isEqualTo("JsonParseException");
        assertThat(msg.getErrorMsg()).contains("Unexpected character");
        assertThat(msg.getStatus()).isEqualTo("PENDING");
        assertThat(msg.getRequeueCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("findById - should return REQUEUED message with requeue count")
    void findById_shouldReturnRequeuedMessageWithRequeueCount() {
        Optional<TelemetryDlqMessageEntity> result = repository.findById(104L);

        assertThat(result).isPresent();
        TelemetryDlqMessageEntity msg = result.get();
        assertThat(msg.getStatus()).isEqualTo("REQUEUED");
        assertThat(msg.getRequeueCount()).isEqualTo(2);
        assertThat(msg.getLastRequeueAt()).isNotNull();
    }

    @Test
    @DisplayName("save - should persist new DLQ message")
    void save_shouldPersistNewDlqMessage() {
        TelemetryDlqMessageEntity newMsg = new TelemetryDlqMessageEntity();
        newMsg.setReceivedAt(Instant.now());
        newMsg.setExchange("test.exchange");
        newMsg.setRoutingKey("test.routing.key");
        newMsg.setPayloadJson("{\"test\":\"data\"}");
        newMsg.setErrorType("TestException");
        newMsg.setErrorMsg("Test error message");
        newMsg.setRunId("run-test-new");
        newMsg.setStatus("PENDING");
        newMsg.setRequeueCount(0);

        TelemetryDlqMessageEntity saved = repository.save(newMsg);

        assertThat(saved.getId()).isNotNull();
        assertThat(repository.count()).isEqualTo(9);
        assertThat(repository.countByStatus("PENDING")).isEqualTo(5);
    }

    @Test
    @DisplayName("save - should update existing message status")
    void save_shouldUpdateExistingMessageStatus() {
        TelemetryDlqMessageEntity msg = repository.findById(101L).orElseThrow();
        assertThat(msg.getStatus()).isEqualTo("PENDING");

        msg.setStatus("REQUEUED");
        msg.setRequeueCount(1);
        msg.setLastRequeueAt(Instant.now());
        repository.save(msg);

        TelemetryDlqMessageEntity updated = repository.findById(101L).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("REQUEUED");
        assertThat(updated.getRequeueCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("deleteById - should remove message")
    void deleteById_shouldRemoveMessage() {
        assertThat(repository.existsById(108L)).isTrue();

        repository.deleteById(108L);

        assertThat(repository.existsById(108L)).isFalse();
        assertThat(repository.count()).isEqualTo(7);
    }

    @Test
    @DisplayName("findById - should return message with null runId")
    void findById_shouldReturnMessageWithNullRunId() {
        // Message id=105 has null runId
        Optional<TelemetryDlqMessageEntity> result = repository.findById(105L);

        assertThat(result).isPresent();
        assertThat(result.get().getRunId()).isNull();
        assertThat(result.get().getStatus()).isEqualTo("IGNORED");
    }
}
