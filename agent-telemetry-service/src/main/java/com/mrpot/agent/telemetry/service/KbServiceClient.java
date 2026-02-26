package com.mrpot.agent.telemetry.service;

import com.mrpot.agent.common.kb.KbDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Client for communicating with the agent-knowledge service.
 * Used to fetch KB document content for enriching run details.
 */
@Slf4j
@Service
public class KbServiceClient {

    private final RestTemplate restTemplate;
    private final String kbServiceUrl;

    public KbServiceClient(
            @Value("${kb.service.url:http://agent-knowledge:8083}") String kbServiceUrl) {
        this.restTemplate = new RestTemplate();
        this.kbServiceUrl = kbServiceUrl;
        log.info("KbServiceClient initialized with URL: {}", kbServiceUrl);
    }

    /**
     * Fetch multiple KB documents by their IDs.
     *
     * @param docIds comma-separated document IDs (e.g., "1,2,3")
     * @return concatenated content text from all documents, or empty string on error
     */
    public String fetchKbContextText(String docIds) {
        if (docIds == null || docIds.isBlank()) {
            log.debug("No docIds provided, returning empty string");
            return "";
        }

        try {
            List<String> ids = List.of(docIds.split(","))
                .stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

            if (ids.isEmpty()) {
                log.debug("No valid IDs parsed from docIds: '{}'", docIds);
                return "";
            }

            String url = kbServiceUrl + "/kb/documents/batch";
            log.debug("Fetching KB documents from {} with IDs: {}", url, ids);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<List<String>> request = new HttpEntity<>(ids, headers);

            ResponseEntity<List<KbDocument>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<List<KbDocument>>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<KbDocument> docs = response.getBody();
                log.debug("Received {} documents from KB service", docs.size());
                
                String result = docs.stream()
                    .map(doc -> {
                        String title = doc.title() != null ? "### " + doc.title() + "\n\n" : "";
                        String content = doc.content() != null ? doc.content() : "";
                        log.trace("Document {}: title='{}', contentLength={}", 
                            doc.id(), doc.title(), content.length());
                        return title + content;
                    })
                    .collect(Collectors.joining("\n\n---\n\n"));
                
                log.debug("Built kbContextText with length: {}", result.length());
                return result;
            }
            
            log.warn("KB service returned non-success status: {}", response.getStatusCode());
            return "";
        } catch (Exception e) {
            log.warn("Failed to fetch KB documents for IDs '{}': {}", docIds, e.getMessage());
            return "";
        }
    }
}
