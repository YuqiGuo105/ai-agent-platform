package com.mrpot.agent.telemetry.repository;

import com.mrpot.agent.telemetry.entity.TelemetryDlqMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for TelemetryDlqMessage entities.
 * Provides methods for querying DLQ messages by status and other criteria.
 */
@Repository
public interface TelemetryDlqMessageJpaRepository extends JpaRepository<TelemetryDlqMessageEntity, Long> {

    /**
     * Find DLQ messages by status, ordered by received time descending (newest first).
     */
    List<TelemetryDlqMessageEntity> findByStatusOrderByReceivedAtDesc(String status);

    /**
     * Find DLQ messages by run ID.
     */
    List<TelemetryDlqMessageEntity> findByRunId(String runId);

    /**
     * Count DLQ messages by status.
     */
    long countByStatus(String status);
}
