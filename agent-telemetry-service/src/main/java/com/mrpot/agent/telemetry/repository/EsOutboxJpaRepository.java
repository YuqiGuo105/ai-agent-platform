package com.mrpot.agent.telemetry.repository;

import com.mrpot.agent.telemetry.entity.EsOutboxEntity;
import com.mrpot.agent.telemetry.entity.EsOutboxEntity.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for EsOutbox entities (Elasticsearch outbox pattern).
 */
@Repository
public interface EsOutboxJpaRepository extends JpaRepository<EsOutboxEntity, Long> {
    
    /**
     * Find pending entries ready to be processed.
     * 
     * @param status the status to filter by
     * @param now current time for retry check
     * @param pageable pagination settings
     * @return list of outbox entries ready to process
     */
    @Query("SELECT e FROM EsOutboxEntity e WHERE e.status = :status AND e.nextRetryAt <= :now ORDER BY e.createdAt ASC")
    List<EsOutboxEntity> findReadyToProcess(
        @Param("status") OutboxStatus status,
        @Param("now") Instant now,
        Pageable pageable
    );
    
    /**
     * Find pending entries ready to be processed (convenience method).
     */
    default List<EsOutboxEntity> findPendingReady(Instant now, int limit) {
        return findReadyToProcess(OutboxStatus.PENDING, now, Pageable.ofSize(limit));
    }
    
    /**
     * Count pending entries.
     */
    long countByStatus(OutboxStatus status);
    
    /**
     * Find entries by run ID.
     */
    List<EsOutboxEntity> findByRunId(String runId);
    
    /**
     * Delete old sent entries (cleanup).
     * 
     * @param status status to delete
     * @param before delete entries older than this
     * @return number of deleted entries
     */
    @Modifying
    @Query("DELETE FROM EsOutboxEntity e WHERE e.status = :status AND e.updatedAt < :before")
    int deleteOldEntries(@Param("status") OutboxStatus status, @Param("before") Instant before);
    
    /**
     * Delete sent entries older than specified time.
     */
    default int cleanupSentEntries(Instant olderThan) {
        return deleteOldEntries(OutboxStatus.SENT, olderThan);
    }
}
