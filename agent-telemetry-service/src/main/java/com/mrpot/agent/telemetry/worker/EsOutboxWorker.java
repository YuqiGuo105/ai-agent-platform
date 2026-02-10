package com.mrpot.agent.telemetry.worker;

import com.mrpot.agent.telemetry.entity.EsOutboxEntity;
import com.mrpot.agent.telemetry.entity.EsOutboxEntity.OutboxStatus;
import com.mrpot.agent.telemetry.repository.EsOutboxJpaRepository;
import com.mrpot.agent.telemetry.service.ElasticsearchService;
import com.mrpot.agent.telemetry.service.ElasticsearchService.BulkOperation;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Background worker that processes the ES outbox table.
 * 
 * Features:
 * - Batch processing for efficiency
 * - Exponential backoff on failures
 * - Automatic cleanup of old sent entries
 * - Configurable batch size and retry limits
 */
@Component
@RequiredArgsConstructor
public class EsOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(EsOutboxWorker.class);
    
    private final EsOutboxJpaRepository outboxRepo;
    private final ElasticsearchService esService;

    @Value("${app.es-outbox.batch-size:100}")
    private int batchSize;

    @Value("${app.es-outbox.max-retries:5}")
    private int maxRetries;

    @Value("${app.es-outbox.cleanup-days:7}")
    private int cleanupDays;

    @Value("${app.es-outbox.enabled:true}")
    private boolean enabled;

    @PostConstruct
    public void init() {
        if (enabled) {
            log.info("ES Outbox Worker initialized: batchSize={}, maxRetries={}", 
                batchSize, maxRetries);
            // Ensure indices exist on startup
            try {
                esService.ensureIndices();
            } catch (Exception e) {
                log.warn("Failed to ensure ES indices on startup: {}", e.getMessage());
            }
        }
    }

    /**
     * Process pending outbox entries.
     * Runs every 5 seconds.
     */
    @Scheduled(fixedDelayString = "${app.es-outbox.poll-interval-ms:5000}")
    @Transactional
    public void processPendingEntries() {
        if (!enabled) return;
        
        try {
            Instant now = Instant.now();
            List<EsOutboxEntity> entries = outboxRepo.findPendingReady(now, batchSize);
            
            if (entries.isEmpty()) {
                return;
            }
            
            log.debug("Processing {} outbox entries", entries.size());
            
            // Group by whether we can batch (same index)
            Map<String, List<EsOutboxEntity>> byIndex = entries.stream()
                .collect(Collectors.groupingBy(EsOutboxEntity::getIndexName));
            
            for (Map.Entry<String, List<EsOutboxEntity>> entry : byIndex.entrySet()) {
                processBatch(entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            log.error("Error processing outbox: {}", e.getMessage(), e);
        }
    }

    private void processBatch(String indexName, List<EsOutboxEntity> entries) {
        // Build bulk operations
        List<BulkOperation> operations = entries.stream()
            .map(e -> new BulkOperation(e.getIndexName(), e.getDocId(), e.getDocJson()))
            .toList();
        
        // Execute bulk index
        Map<String, Boolean> results = esService.bulkIndex(operations);
        
        // Update outbox entries based on results
        for (EsOutboxEntity entry : entries) {
            Boolean success = results.get(entry.getDocId());
            
            if (Boolean.TRUE.equals(success)) {
                // Mark as sent
                entry.setStatus(OutboxStatus.SENT);
                entry.setLastError(null);
            } else {
                // Increment retry count
                int newRetryCount = entry.getRetryCount() + 1;
                entry.setRetryCount(newRetryCount);
                
                if (newRetryCount >= maxRetries) {
                    // Mark as permanently failed
                    entry.setStatus(OutboxStatus.FAILED);
                    entry.setLastError("Max retries exceeded");
                    log.warn("Outbox entry permanently failed: id={}, docId={}", 
                        entry.getId(), entry.getDocId());
                } else {
                    // Set next retry time with exponential backoff
                    entry.setNextRetryAt(EsOutboxEntity.calculateNextRetry(newRetryCount));
                    entry.setLastError("Index failed");
                }
            }
            
            outboxRepo.save(entry);
        }
        
        long successCount = results.values().stream()
            .filter(Boolean.TRUE::equals).count();
        log.debug("Batch result: index={}, total={}, success={}", 
            indexName, entries.size(), successCount);
    }

    /**
     * Cleanup old sent entries.
     * Runs once per hour.
     */
    @Scheduled(fixedDelayString = "${app.es-outbox.cleanup-interval-ms:3600000}")
    @Transactional
    public void cleanupOldEntries() {
        if (!enabled) return;
        
        try {
            Instant cutoff = Instant.now().minus(cleanupDays, ChronoUnit.DAYS);
            int deleted = outboxRepo.cleanupSentEntries(cutoff);
            if (deleted > 0) {
                log.info("Cleaned up {} old outbox entries", deleted);
            }
        } catch (Exception e) {
            log.error("Error cleaning up outbox: {}", e.getMessage());
        }
    }

    /**
     * Log outbox statistics.
     * Runs every minute.
     */
    @Scheduled(fixedDelayString = "${app.es-outbox.stats-interval-ms:60000}")
    public void logStats() {
        if (!enabled) return;
        
        try {
            long pending = outboxRepo.countByStatus(OutboxStatus.PENDING);
            long failed = outboxRepo.countByStatus(OutboxStatus.FAILED);
            
            if (pending > 0 || failed > 0) {
                log.info("Outbox stats: pending={}, failed={}", pending, failed);
            }
        } catch (Exception e) {
            log.debug("Error getting outbox stats: {}", e.getMessage());
        }
    }
}
