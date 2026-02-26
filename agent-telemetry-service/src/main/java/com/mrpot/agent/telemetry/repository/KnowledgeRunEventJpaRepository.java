package com.mrpot.agent.telemetry.repository;

import com.mrpot.agent.telemetry.entity.KnowledgeRunEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for KnowledgeRunEvent entities (idempotency tracking).
 */
@Repository
public interface KnowledgeRunEventJpaRepository extends JpaRepository<KnowledgeRunEventEntity, String> {
    
    /**
     * Find all events for a specific run.
     */
    List<KnowledgeRunEventEntity> findByRunId(String runId);
    
    /**
     * Check if an event has been processed.
     */
    boolean existsByEventId(String eventId);
    
    /**
     * Delete all events for a specific run.
     * @return the number of entities deleted
     */
    int deleteByRunId(String runId);
}
