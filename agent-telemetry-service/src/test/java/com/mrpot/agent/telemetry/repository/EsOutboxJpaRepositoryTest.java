package com.mrpot.agent.telemetry.repository;

import com.mrpot.agent.telemetry.entity.EsOutboxEntity;
import com.mrpot.agent.telemetry.entity.EsOutboxEntity.OutboxStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository tests for EsOutboxJpaRepository.
 * Uses @DataJpaTest for in-memory database testing with SQL test data.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "classpath:test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class EsOutboxJpaRepositoryTest {

    @Autowired
    private EsOutboxJpaRepository repository;

    @Test
    @DisplayName("findReadyToProcess - should return PENDING entries with nextRetryAt <= now")
    void findReadyToProcess_shouldReturnPendingEntriesReadyToProcess() {
        // Use a future time to include all PENDING entries
        Instant futureTime = Instant.parse("2027-01-01T00:00:00Z");
        
        List<EsOutboxEntity> result = repository.findReadyToProcess(
            OutboxStatus.PENDING, 
            futureTime, 
            Pageable.ofSize(10)
        );

        // PENDING entries: 101, 102, 104
        assertThat(result).hasSize(3);
        assertThat(result).allMatch(e -> e.getStatus() == OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("findReadyToProcess - should respect limit")
    void findReadyToProcess_shouldRespectLimit() {
        Instant futureTime = Instant.parse("2027-01-01T00:00:00Z");
        
        List<EsOutboxEntity> result = repository.findReadyToProcess(
            OutboxStatus.PENDING, 
            futureTime, 
            Pageable.ofSize(2)
        );

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("findReadyToProcess - should return empty when no entries match")
    void findReadyToProcess_shouldReturnEmptyWhenNoEntriesMatch() {
        // Use a very old time - no entries should have nextRetryAt <= this
        Instant pastTime = Instant.parse("2020-01-01T00:00:00Z");
        
        List<EsOutboxEntity> result = repository.findReadyToProcess(
            OutboxStatus.PENDING, 
            pastTime, 
            Pageable.ofSize(10)
        );

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findPendingReady - should use default status PENDING")
    void findPendingReady_shouldUseDefaultStatusPending() {
        Instant futureTime = Instant.parse("2027-01-01T00:00:00Z");
        
        List<EsOutboxEntity> result = repository.findPendingReady(futureTime, 10);

        assertThat(result).hasSize(3);
        assertThat(result).allMatch(e -> e.getStatus() == OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("countByStatus - should count PENDING entries")
    void countByStatus_shouldCountPendingEntries() {
        long count = repository.countByStatus(OutboxStatus.PENDING);

        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("countByStatus - should count SENT entries")
    void countByStatus_shouldCountSentEntries() {
        long count = repository.countByStatus(OutboxStatus.SENT);

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("countByStatus - should count FAILED entries")
    void countByStatus_shouldCountFailedEntries() {
        long count = repository.countByStatus(OutboxStatus.FAILED);

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("findByRunId - should return entries for specific run")
    void findByRunId_shouldReturnEntriesForRun() {
        List<EsOutboxEntity> result = repository.findByRunId("run-001-uuid");

        // run-001-uuid has 2 entries: mrpot_runs and mrpot_tool_calls
        assertThat(result).hasSize(2);
        assertThat(result).extracting(EsOutboxEntity::getIndexName)
            .containsExactlyInAnyOrder("mrpot_runs", "mrpot_tool_calls");
    }

    @Test
    @DisplayName("findByRunId - should return empty for non-existing run")
    void findByRunId_shouldReturnEmptyForNonExistingRun() {
        List<EsOutboxEntity> result = repository.findByRunId("non-existing-run");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findAll - should return all 6 entries")
    void findAll_shouldReturnAllEntries() {
        List<EsOutboxEntity> result = repository.findAll();

        assertThat(result).hasSize(6);
    }

    @Test
    @DisplayName("findById - should return entry with all fields")
    void findById_shouldReturnEntryWithAllFields() {
        Optional<EsOutboxEntity> result = repository.findById(101L);

        assertThat(result).isPresent();
        EsOutboxEntity entry = result.get();
        assertThat(entry.getRunId()).isEqualTo("run-001-uuid");
        assertThat(entry.getIndexName()).isEqualTo("mrpot_runs");
        assertThat(entry.getDocId()).isEqualTo("run-001-uuid");
        assertThat(entry.getDocJson()).contains("deepseek");
        assertThat(entry.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(entry.getRetryCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("findById - should return FAILED entry with error details")
    void findById_shouldReturnFailedEntryWithErrorDetails() {
        Optional<EsOutboxEntity> result = repository.findById(105L);

        assertThat(result).isPresent();
        EsOutboxEntity entry = result.get();
        assertThat(entry.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(entry.getRetryCount()).isEqualTo(5);
        assertThat(entry.getLastError()).contains("Connection refused");
    }

    @Test
    @DisplayName("save - should persist new entry")
    void save_shouldPersistNewEntry() {
        EsOutboxEntity newEntry = new EsOutboxEntity();
        newEntry.setRunId("run-new-test");
        newEntry.setIndexName("mrpot_runs");
        newEntry.setDocId("run-new-test");
        newEntry.setDocJson("{\"test\":\"data\"}");
        newEntry.setStatus(OutboxStatus.PENDING);
        newEntry.setRetryCount(0);
        newEntry.setCreatedAt(Instant.now());
        newEntry.setUpdatedAt(Instant.now());
        newEntry.setNextRetryAt(Instant.now());

        EsOutboxEntity saved = repository.save(newEntry);

        assertThat(saved.getId()).isNotNull();
        assertThat(repository.count()).isEqualTo(7);
    }

    @Test
    @DisplayName("save - should update existing entry status")
    void save_shouldUpdateExistingEntryStatus() {
        EsOutboxEntity entry = repository.findById(101L).orElseThrow();
        assertThat(entry.getStatus()).isEqualTo(OutboxStatus.PENDING);

        entry.setStatus(OutboxStatus.SENT);
        entry.setUpdatedAt(Instant.now());
        repository.save(entry);

        EsOutboxEntity updated = repository.findById(101L).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    @Test
    @DisplayName("deleteOldEntries - should delete SENT entries older than threshold")
    void deleteOldEntries_shouldDeleteSentEntriesOlderThanThreshold() {
        // Delete SENT entries older than a future date (should delete all SENT)
        Instant futureDate = Instant.parse("2030-01-01T00:00:00Z");
        
        int deleted = repository.deleteOldEntries(OutboxStatus.SENT, futureDate);

        assertThat(deleted).isEqualTo(2);
        assertThat(repository.countByStatus(OutboxStatus.SENT)).isEqualTo(0);
        assertThat(repository.count()).isEqualTo(4); // 6 - 2 = 4
    }

    @Test
    @DisplayName("deleteOldEntries - should not delete entries newer than threshold")
    void deleteOldEntries_shouldNotDeleteEntriesNewerThanThreshold() {
        // Use a very old date - no entries should be deleted
        Instant oldDate = Instant.parse("2020-01-01T00:00:00Z");
        
        int deleted = repository.deleteOldEntries(OutboxStatus.SENT, oldDate);

        assertThat(deleted).isEqualTo(0);
        assertThat(repository.count()).isEqualTo(6);
    }

    @Test
    @DisplayName("cleanupSentEntries - should delete old SENT entries")
    void cleanupSentEntries_shouldDeleteOldSentEntries() {
        Instant futureDate = Instant.parse("2030-01-01T00:00:00Z");
        
        int deleted = repository.cleanupSentEntries(futureDate);

        assertThat(deleted).isEqualTo(2);
    }

    @Test
    @DisplayName("findReadyToProcess - should order by createdAt ASC")
    void findReadyToProcess_shouldOrderByCreatedAtAsc() {
        Instant futureTime = Instant.parse("2027-01-01T00:00:00Z");
        
        List<EsOutboxEntity> result = repository.findReadyToProcess(
            OutboxStatus.PENDING, 
            futureTime, 
            Pageable.ofSize(10)
        );

        // Should be ordered by createdAt ASC (oldest first)
        // Entry 102 at 10:00:02, Entry 101 at 10:00:15, Entry 104 at 10:10:00
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getId()).isEqualTo(102L);
        assertThat(result.get(1).getId()).isEqualTo(101L);
        assertThat(result.get(2).getId()).isEqualTo(104L);
    }

    @Test
    @DisplayName("deleteById - should remove entry")
    void deleteById_shouldRemoveEntry() {
        assertThat(repository.existsById(106L)).isTrue();

        repository.deleteById(106L);

        assertThat(repository.existsById(106L)).isFalse();
        assertThat(repository.count()).isEqualTo(5);
    }
}
