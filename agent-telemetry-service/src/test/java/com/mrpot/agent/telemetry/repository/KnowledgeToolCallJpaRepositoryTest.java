package com.mrpot.agent.telemetry.repository;

import com.mrpot.agent.telemetry.entity.KnowledgeToolCallEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository tests for KnowledgeToolCallJpaRepository.
 * Uses @DataJpaTest for in-memory database testing with SQL test data.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "classpath:test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class KnowledgeToolCallJpaRepositoryTest {

    @Autowired
    private KnowledgeToolCallJpaRepository repository;

    @Test
    @DisplayName("findByRunId - should return tool calls for run-001")
    void findByRunId_shouldReturnToolCallsForRun() {
        List<KnowledgeToolCallEntity> result = repository.findByRunId("run-001-uuid");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(KnowledgeToolCallEntity::getToolName)
            .containsExactlyInAnyOrder("kb_search", "llm_generate");
    }

    @Test
    @DisplayName("findByRunId - should return empty list for non-existing run")
    void findByRunId_shouldReturnEmptyListForNonExistingRun() {
        List<KnowledgeToolCallEntity> result = repository.findByRunId("non-existing-run");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByRunId - should return 3 tool calls for run-002")
    void findByRunId_shouldReturnThreeToolCallsForRun002() {
        List<KnowledgeToolCallEntity> result = repository.findByRunId("run-002-uuid");

        assertThat(result).hasSize(3);
        assertThat(result).extracting(KnowledgeToolCallEntity::getToolName)
            .containsExactlyInAnyOrder("kb_search", "web_search", "llm_generate");
    }

    @Test
    @DisplayName("findByRunIdOrderByCreatedAt - should return ordered tool calls")
    void findByRunIdOrderByCreatedAt_shouldReturnOrderedToolCalls() {
        List<KnowledgeToolCallEntity> result = repository.findByRunIdOrderByCreatedAt("run-001-uuid");

        assertThat(result).hasSize(2);
        // tc-001 created at 10:00:02, tc-002 at 10:00:03
        assertThat(result.get(0).getId()).isEqualTo("tc-001");
        assertThat(result.get(1).getId()).isEqualTo("tc-002");
    }

    @Test
    @DisplayName("findByToolName - should find kb_search tool calls")
    void findByToolName_shouldFindKbSearchToolCalls() {
        List<KnowledgeToolCallEntity> result = repository.findByToolName("kb_search");

        // run-001, run-002, run-004 (2 attempts), run-005 = 5 calls
        assertThat(result).hasSize(5);
        assertThat(result).allMatch(tc -> tc.getToolName().equals("kb_search"));
    }

    @Test
    @DisplayName("findByToolName - should find llm_generate tool calls")
    void findByToolName_shouldFindLlmGenerateToolCalls() {
        List<KnowledgeToolCallEntity> result = repository.findByToolName("llm_generate");

        // run-001, run-002, run-005 = 3 calls
        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("findByToolName - should return empty for non-existing tool")
    void findByToolName_shouldReturnEmptyForNonExistingTool() {
        List<KnowledgeToolCallEntity> result = repository.findByToolName("non_existing_tool");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("countByRunId - should return correct count")
    void countByRunId_shouldReturnCorrectCount() {
        long count = repository.countByRunId("run-002-uuid");

        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("countByRunId - should return 0 for non-existing run")
    void countByRunId_shouldReturnZeroForNonExistingRun() {
        long count = repository.countByRunId("non-existing-run");

        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("findAll - should return all 9 tool calls")
    void findAll_shouldReturnAllToolCalls() {
        List<KnowledgeToolCallEntity> result = repository.findAll();

        assertThat(result).hasSize(9);
    }

    @Test
    @DisplayName("findById - should return tool call with all fields")
    void findById_shouldReturnToolCallWithAllFields() {
        var result = repository.findById("tc-001");

        assertThat(result).isPresent();
        var tc = result.get();
        assertThat(tc.getRunId()).isEqualTo("run-001-uuid");
        assertThat(tc.getToolName()).isEqualTo("kb_search");
        assertThat(tc.getAttempt()).isEqualTo(1);
        assertThat(tc.getOk()).isTrue();
        assertThat(tc.getDurationMs()).isEqualTo(250);
        assertThat(tc.getCacheHit()).isFalse();
        assertThat(tc.getFreshness()).isEqualTo("FRESH");
    }

    @Test
    @DisplayName("findById - should return failed tool call with error details")
    void findById_shouldReturnFailedToolCallWithErrorDetails() {
        var result = repository.findById("tc-006");

        assertThat(result).isPresent();
        var tc = result.get();
        assertThat(tc.getOk()).isFalse();
        assertThat(tc.getErrorCode()).isEqualTo("TIMEOUT");
        assertThat(tc.getErrorMsg()).contains("Database query timeout");
        assertThat(tc.getRetryable()).isTrue();
    }

    @Test
    @DisplayName("save - should persist new tool call")
    void save_shouldPersistNewToolCall() {
        KnowledgeToolCallEntity newTc = new KnowledgeToolCallEntity();
        newTc.setId("tc-new-test");
        newTc.setRunId("run-001-uuid");
        newTc.setToolName("test_tool");
        newTc.setAttempt(1);
        newTc.setOk(true);
        newTc.setDurationMs(100L);
        newTc.setCalledAt(Instant.now());
        newTc.setCreatedAt(Instant.now());

        repository.save(newTc);

        assertThat(repository.findById("tc-new-test")).isPresent();
        assertThat(repository.countByRunId("run-001-uuid")).isEqualTo(3);
    }

    @Test
    @DisplayName("findByRunId - should return failed tool calls for run-004")
    void findByRunId_shouldReturnFailedToolCallsForRun004() {
        List<KnowledgeToolCallEntity> result = repository.findByRunId("run-004-uuid");

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(tc -> !tc.getOk());
        assertThat(result).extracting(KnowledgeToolCallEntity::getAttempt)
            .containsExactlyInAnyOrder(1, 2);
    }
}
