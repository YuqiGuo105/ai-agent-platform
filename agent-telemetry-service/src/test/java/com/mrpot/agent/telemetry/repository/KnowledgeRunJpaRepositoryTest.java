package com.mrpot.agent.telemetry.repository;

import com.mrpot.agent.telemetry.entity.KnowledgeRunEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository tests for KnowledgeRunJpaRepository.
 * Uses @DataJpaTest for in-memory database testing with SQL test data.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "classpath:test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class KnowledgeRunJpaRepositoryTest {

    @Autowired
    private KnowledgeRunJpaRepository repository;

    @Test
    @DisplayName("findById - should return existing run")
    void findById_shouldReturnExistingRun() {
        Optional<KnowledgeRunEntity> result = repository.findById("run-001-uuid");

        assertThat(result).isPresent();
        assertThat(result.get().getTraceId()).isEqualTo("trace-xyz-001");
        assertThat(result.get().getSessionId()).isEqualTo("session-abc-123");
        assertThat(result.get().getStatus()).isEqualTo("DONE");
        assertThat(result.get().getModel()).isEqualTo("deepseek");
    }

    @Test
    @DisplayName("findById - should return empty for non-existing run")
    void findById_shouldReturnEmptyForNonExistingRun() {
        Optional<KnowledgeRunEntity> result = repository.findById("non-existing-id");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findAll - should return all 5 runs")
    void findAll_shouldReturnAllRuns() {
        List<KnowledgeRunEntity> result = repository.findAll();

        assertThat(result).hasSize(5);
    }

    @Test
    @DisplayName("count - should return correct count")
    void count_shouldReturnCorrectCount() {
        long count = repository.count();

        assertThat(count).isEqualTo(5);
    }

    @Test
    @DisplayName("existsById - should return true for existing run")
    void existsById_shouldReturnTrueForExistingRun() {
        boolean exists = repository.existsById("run-002-uuid");

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsById - should return false for non-existing run")
    void existsById_shouldReturnFalseForNonExistingRun() {
        boolean exists = repository.existsById("non-existing-id");

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("save - should persist new run")
    void save_shouldPersistNewRun() {
        KnowledgeRunEntity newRun = new KnowledgeRunEntity();
        newRun.setId("run-new-test");
        newRun.setTraceId("trace-new-test");
        newRun.setSessionId("session-new-test");
        newRun.setUserId("user-test");
        newRun.setMode("GENERAL");
        newRun.setModel("gpt-4o");
        newRun.setQuestion("Test question?");
        newRun.setStatus("RUNNING");

        KnowledgeRunEntity saved = repository.save(newRun);

        assertThat(saved.getId()).isEqualTo("run-new-test");
        assertThat(repository.count()).isEqualTo(6);
    }

    @Test
    @DisplayName("save - should update existing run")
    void save_shouldUpdateExistingRun() {
        KnowledgeRunEntity run = repository.findById("run-003-uuid").orElseThrow();
        assertThat(run.getStatus()).isEqualTo("RUNNING");

        run.setStatus("DONE");
        run.setAnswerFinal("Updated answer");
        repository.save(run);

        KnowledgeRunEntity updated = repository.findById("run-003-uuid").orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("DONE");
        assertThat(updated.getAnswerFinal()).isEqualTo("Updated answer");
    }

    @Test
    @DisplayName("deleteById - should remove run")
    void deleteById_shouldRemoveRun() {
        assertThat(repository.existsById("run-005-uuid")).isTrue();

        repository.deleteById("run-005-uuid");

        assertThat(repository.existsById("run-005-uuid")).isFalse();
        assertThat(repository.count()).isEqualTo(4);
    }

    @Test
    @DisplayName("findById - should retrieve FAILED run with error code")
    void findById_shouldRetrieveFailedRunWithErrorCode() {
        Optional<KnowledgeRunEntity> result = repository.findById("run-004-uuid");

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo("FAILED");
        assertThat(result.get().getErrorCode()).isEqualTo("TIMEOUT");
    }
}
