package com.mrpot.agent.telemetry.repository;

import com.mrpot.agent.telemetry.entity.KnowledgeRunEventEntity;
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
 * Repository tests for KnowledgeRunEventJpaRepository.
 * Uses @DataJpaTest for in-memory database testing with SQL test data.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "classpath:test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class KnowledgeRunEventJpaRepositoryTest {

    @Autowired
    private KnowledgeRunEventJpaRepository repository;

    @Test
    @DisplayName("findByRunId - should return events for run-001")
    void findByRunId_shouldReturnEventsForRun001() {
        List<KnowledgeRunEventEntity> result = repository.findByRunId("run-001-uuid");

        // run-001-uuid has 4 events: run.start, run.final, tool.end (tc-001), tool.end (tc-002)
        assertThat(result).hasSize(4);
        assertThat(result).extracting(KnowledgeRunEventEntity::getEventType)
            .containsExactlyInAnyOrder("run.start", "run.final", "tool.end", "tool.end");
    }

    @Test
    @DisplayName("findByRunId - should return events for run-002")
    void findByRunId_shouldReturnEventsForRun002() {
        List<KnowledgeRunEventEntity> result = repository.findByRunId("run-002-uuid");

        // run-002-uuid has 2 events: run.start, run.final
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("findByRunId - should return empty for non-existing run")
    void findByRunId_shouldReturnEmptyForNonExistingRun() {
        List<KnowledgeRunEventEntity> result = repository.findByRunId("non-existing-run");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("existsByEventId - should return true for existing event")
    void existsByEventId_shouldReturnTrueForExistingEvent() {
        boolean exists = repository.existsByEventId("run-001-uuid:run.start");

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByEventId - should return true for tool event")
    void existsByEventId_shouldReturnTrueForToolEvent() {
        boolean exists = repository.existsByEventId("run-001-uuid:tool.end:tc-001");

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByEventId - should return false for non-existing event")
    void existsByEventId_shouldReturnFalseForNonExistingEvent() {
        boolean exists = repository.existsByEventId("non-existing-event");

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("existsByEventId - should return false for unprocessed event")
    void existsByEventId_shouldReturnFalseForUnprocessedEvent() {
        // Event that doesn't exist - run-005 run.start was not processed
        boolean exists = repository.existsByEventId("run-005-uuid:run.start");

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("findAll - should return all 8 events")
    void findAll_shouldReturnAllEvents() {
        List<KnowledgeRunEventEntity> result = repository.findAll();

        assertThat(result).hasSize(8);
    }

    @Test
    @DisplayName("findById - should return event with all fields")
    void findById_shouldReturnEventWithAllFields() {
        Optional<KnowledgeRunEventEntity> result = repository.findById("run-001-uuid:run.start");

        assertThat(result).isPresent();
        KnowledgeRunEventEntity event = result.get();
        assertThat(event.getEventId()).isEqualTo("run-001-uuid:run.start");
        assertThat(event.getRunId()).isEqualTo("run-001-uuid");
        assertThat(event.getEventType()).isEqualTo("run.start");
        assertThat(event.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("findById - should return tool event")
    void findById_shouldReturnToolEvent() {
        Optional<KnowledgeRunEventEntity> result = repository.findById("run-001-uuid:tool.end:tc-001");

        assertThat(result).isPresent();
        KnowledgeRunEventEntity event = result.get();
        assertThat(event.getEventType()).isEqualTo("tool.end");
        assertThat(event.getRunId()).isEqualTo("run-001-uuid");
    }

    @Test
    @DisplayName("save - should persist new event")
    void save_shouldPersistNewEvent() {
        KnowledgeRunEventEntity newEvent = new KnowledgeRunEventEntity();
        newEvent.setEventId("run-new-test:run.start");
        newEvent.setRunId("run-new-test");
        newEvent.setEventType("run.start");
        newEvent.setProcessedAt(Instant.now());

        repository.save(newEvent);

        assertThat(repository.existsByEventId("run-new-test:run.start")).isTrue();
        assertThat(repository.count()).isEqualTo(9);
    }

    @Test
    @DisplayName("save - should handle duplicate event gracefully")
    void save_shouldHandleDuplicateEvent() {
        // The event already exists
        assertThat(repository.existsByEventId("run-001-uuid:run.start")).isTrue();
        
        long countBefore = repository.count();

        KnowledgeRunEventEntity existingEvent = repository.findById("run-001-uuid:run.start").orElseThrow();
        repository.save(existingEvent);

        // Count should remain the same
        assertThat(repository.count()).isEqualTo(countBefore);
    }

    @Test
    @DisplayName("deleteById - should remove event")
    void deleteById_shouldRemoveEvent() {
        assertThat(repository.existsByEventId("run-004-uuid:run.failed")).isTrue();

        repository.deleteById("run-004-uuid:run.failed");

        assertThat(repository.existsByEventId("run-004-uuid:run.failed")).isFalse();
        assertThat(repository.count()).isEqualTo(7);
    }

    @Test
    @DisplayName("count - should return correct count")
    void count_shouldReturnCorrectCount() {
        long count = repository.count();

        assertThat(count).isEqualTo(8);
    }

    @Test
    @DisplayName("findByRunId - should return single event for run-003")
    void findByRunId_shouldReturnSingleEventForRun003() {
        List<KnowledgeRunEventEntity> result = repository.findByRunId("run-003-uuid");

        // run-003-uuid only has run.start (still running)
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventType()).isEqualTo("run.start");
    }

    @Test
    @DisplayName("findByRunId - should return run.failed event for run-004")
    void findByRunId_shouldReturnRunFailedEventForRun004() {
        List<KnowledgeRunEventEntity> result = repository.findByRunId("run-004-uuid");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventType()).isEqualTo("run.failed");
    }

    @Test
    @DisplayName("createRunEventId - should generate correct event ID")
    void createRunEventId_shouldGenerateCorrectEventId() {
        String eventId = KnowledgeRunEventEntity.createRunEventId("run-123", "run.start");

        assertThat(eventId).isEqualTo("run-123:run.start");
    }

    @Test
    @DisplayName("createToolEventId - should generate correct event ID")
    void createToolEventId_shouldGenerateCorrectEventId() {
        String eventId = KnowledgeRunEventEntity.createToolEventId("run-123", "tool.end", "tc-456");

        assertThat(eventId).isEqualTo("run-123:tool.end:tc-456");
    }
}
