package com.mrpot.agent.telemetry.repository;

import com.mrpot.agent.telemetry.entity.KnowledgeToolCallEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for KnowledgeToolCall entities.
 */
@Repository
public interface KnowledgeToolCallJpaRepository extends JpaRepository<KnowledgeToolCallEntity, String> {
    
    /**
     * Find all tool calls for a specific run.
     */
    List<KnowledgeToolCallEntity> findByRunId(String runId);

    /**
     * Find all tool calls for a specific run, ordered by creation time.
     */
    List<KnowledgeToolCallEntity> findByRunIdOrderByCreatedAt(String runId);
    
    /**
     * Find all tool calls for a specific tool name.
     */
    List<KnowledgeToolCallEntity> findByToolName(String toolName);
    
    /**
     * Count tool calls by run ID.
     */
    long countByRunId(String runId);
}
