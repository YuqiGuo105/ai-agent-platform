package com.mrpot.agent.telemetry.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Elasticsearch client service for indexing telemetry documents.
 * Uses REST API for compatibility and simplicity.
 */
@Service
public class ElasticsearchService {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchService.class);
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String authHeader;

    public ElasticsearchService(
            ObjectMapper objectMapper,
            @Value("${app.elastic.base-url:http://localhost:9200}") String baseUrl,
            @Value("${app.elastic.username:}") String username,
            @Value("${app.elastic.password:}") String password) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        
        // Build auth header if credentials provided
        if (username != null && !username.isEmpty()) {
            String auth = username + ":" + password;
            this.authHeader = "Basic " + Base64.getEncoder()
                .encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        } else {
            this.authHeader = null;
        }
        
        log.info("Elasticsearch client initialized: {}", baseUrl);
    }

    /**
     * Index a single document.
     * 
     * @param indexName the index name
     * @param docId the document ID
     * @param docJson the document as JSON string
     * @return true if successful
     */
    public boolean indexDocument(String indexName, String docId, String docJson) {
        try {
            String url = String.format("%s/%s/_doc/%s", baseUrl, indexName, docId);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (authHeader != null) {
                headers.set("Authorization", authHeader);
            }
            
            HttpEntity<String> request = new HttpEntity<>(docJson, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.PUT, request, String.class);
            
            boolean success = response.getStatusCode().is2xxSuccessful();
            if (!success) {
                log.warn("ES index failed: index={}, docId={}, status={}", 
                    indexName, docId, response.getStatusCode());
            }
            return success;
        } catch (Exception e) {
            log.error("ES index error: index={}, docId={}, error={}", 
                indexName, docId, e.getMessage());
            return false;
        }
    }

    /**
     * Bulk index multiple documents.
     * 
     * @param operations list of (indexName, docId, docJson) tuples
     * @return map of docId -> success status
     */
    public Map<String, Boolean> bulkIndex(List<BulkOperation> operations) {
        if (operations == null || operations.isEmpty()) {
            return Collections.emptyMap();
        }
        
        Map<String, Boolean> results = new HashMap<>();
        
        try {
            // Build bulk request body
            StringBuilder bulkBody = new StringBuilder();
            for (BulkOperation op : operations) {
                // Action line
                String actionLine = String.format(
                    "{\"index\":{\"_index\":\"%s\",\"_id\":\"%s\"}}\n",
                    op.indexName(), op.docId());
                bulkBody.append(actionLine);
                // Document line
                bulkBody.append(op.docJson()).append("\n");
            }
            
            String url = baseUrl + "/_bulk";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/x-ndjson"));
            if (authHeader != null) {
                headers.set("Authorization", authHeader);
            }
            
            HttpEntity<String> request = new HttpEntity<>(bulkBody.toString(), headers);
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                // Parse response to get individual results
                var respNode = objectMapper.readTree(response.getBody());
                var items = respNode.get("items");
                if (items != null && items.isArray()) {
                    int i = 0;
                    for (var item : items) {
                        if (i < operations.size()) {
                            var indexResult = item.get("index");
                            boolean success = indexResult != null && 
                                (indexResult.get("status").asInt() == 200 || 
                                 indexResult.get("status").asInt() == 201);
                            results.put(operations.get(i).docId(), success);
                        }
                        i++;
                    }
                }
            } else {
                // Mark all as failed
                for (BulkOperation op : operations) {
                    results.put(op.docId(), false);
                }
            }
        } catch (Exception e) {
            log.error("ES bulk index error: {}", e.getMessage());
            // Mark all as failed
            for (BulkOperation op : operations) {
                results.put(op.docId(), false);
            }
        }
        
        return results;
    }

    /**
     * Check if Elasticsearch is reachable.
     */
    public boolean isHealthy() {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (authHeader != null) {
                headers.set("Authorization", authHeader);
            }
            
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/_cluster/health", HttpMethod.GET, request, String.class);
            
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("ES health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Ensure indices exist with proper mappings.
     */
    public void ensureIndices() {
        ensureIndex("mrpot_runs", RUNS_MAPPING);
        ensureIndex("mrpot_tool_calls", TOOL_CALLS_MAPPING);
    }

    private void ensureIndex(String indexName, String mapping) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (authHeader != null) {
                headers.set("Authorization", authHeader);
            }
            
            // Check if index exists
            try {
                HttpEntity<Void> checkRequest = new HttpEntity<>(headers);
                restTemplate.exchange(
                    baseUrl + "/" + indexName, HttpMethod.HEAD, checkRequest, Void.class);
                log.debug("Index {} already exists", indexName);
                return;
            } catch (Exception ignored) {
                // Index doesn't exist, create it
            }
            
            // Create index with mapping
            HttpEntity<String> createRequest = new HttpEntity<>(mapping, headers);
            restTemplate.exchange(
                baseUrl + "/" + indexName, HttpMethod.PUT, createRequest, String.class);
            log.info("Created index: {}", indexName);
        } catch (Exception e) {
            log.warn("Failed to ensure index {}: {}", indexName, e.getMessage());
        }
    }

    /**
     * Record for bulk operation.
     */
    public record BulkOperation(String indexName, String docId, String docJson) {}

    // Index mappings
    private static final String RUNS_MAPPING = """
        {
          "mappings": {
            "properties": {
              "id": { "type": "keyword" },
              "createdAt": { "type": "date" },
              "updatedAt": { "type": "date" },
              "traceId": { "type": "keyword" },
              "sessionId": { "type": "keyword" },
              "userId": { "type": "keyword" },
              "mode": { "type": "keyword" },
              "model": { "type": "keyword" },
              "question": { "type": "text" },
              "answerFinal": { "type": "text" },
              "kbHitCount": { "type": "integer" },
              "kbDocIds": { "type": "keyword" },
              "kbLatencyMs": { "type": "long" },
              "totalLatencyMs": { "type": "long" },
              "status": { "type": "keyword" },
              "errorCode": { "type": "keyword" },
              "parentRunId": { "type": "keyword" },
              "replayMode": { "type": "keyword" }
            }
          }
        }
        """;

    private static final String TOOL_CALLS_MAPPING = """
        {
          "mappings": {
            "properties": {
              "id": { "type": "keyword" },
              "runId": { "type": "keyword" },
              "toolName": { "type": "keyword" },
              "attempt": { "type": "integer" },
              "ok": { "type": "boolean" },
              "durationMs": { "type": "long" },
              "argsDigest": { "type": "keyword" },
              "argsPreview": { "type": "text" },
              "argsSize": { "type": "integer" },
              "resultDigest": { "type": "keyword" },
              "resultPreview": { "type": "text" },
              "resultSize": { "type": "integer" },
              "cacheHit": { "type": "boolean" },
              "ttlHintSeconds": { "type": "long" },
              "freshness": { "type": "keyword" },
              "errorCode": { "type": "keyword" },
              "errorMsg": { "type": "text" },
              "retryable": { "type": "boolean" },
              "keyInfoJson": { "type": "text" },
              "calledAt": { "type": "date" },
              "createdAt": { "type": "date" }
            }
          }
        }
        """;
}
