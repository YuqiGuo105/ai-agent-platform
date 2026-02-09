package com.mrpot.agent.tools.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrpot.agent.common.kb.KbDocument;
import com.mrpot.agent.common.kb.KbSearchRequest;
import com.mrpot.agent.common.kb.KbSearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Client for calling KB retrieval endpoints in agent-service.
 */
@Slf4j
@Component
public class KbServiceClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public KbServiceClient(
            WebClient.Builder webClientBuilder,
            @Value("${agent.service.base-url:http://localhost:8080}") String baseUrl,
            ObjectMapper objectMapper
    ) {
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = objectMapper;
        log.info("KbServiceClient initialized with baseUrl: {}", baseUrl);
    }

    /**
     * Search KB documents via agent-service.
     *
     * @param request search request
     * @return search response with documents
     */
    public KbSearchResponse search(KbSearchRequest request) {
        try {
            String requestJson = objectMapper.writeValueAsString(request);
            log.debug("Calling KB search with request: {}", requestJson);

            String responseJson = webClient
                    .post()
                    .uri("/kb/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestJson)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (responseJson == null || responseJson.isBlank()) {
                log.error("KB search returned empty response");
                return new KbSearchResponse(
                        java.util.List.of(),
                        java.util.List.of(),
                        "",
                        System.currentTimeMillis()
                );
            }

            return objectMapper.readValue(responseJson, KbSearchResponse.class);

        } catch (Exception e) {
            log.error("Error calling KB search: {}", e.getMessage(), e);
            return new KbSearchResponse(
                    java.util.List.of(),
                    java.util.List.of(),
                    "",
                    System.currentTimeMillis()
            );
        }
    }

    /**
     * Get a document by ID via agent-service.
     *
     * @param id document ID
     * @return the document if found, null otherwise
     */
    public KbDocument getDocument(String id) {
        try {
            log.debug("Fetching KB document with ID: {}", id);

            String responseJson = webClient
                    .get()
                    .uri("/kb/documents/{id}", id)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .onErrorResume(e -> {
                        log.error("Error fetching document {}: {}", id, e.getMessage());
                        return Mono.empty();
                    })
                    .block();

            if (responseJson == null || responseJson.isBlank()) {
                log.warn("Document {} not found or empty response", id);
                return null;
            }

            return objectMapper.readValue(responseJson, KbDocument.class);

        } catch (Exception e) {
            log.error("Error getting document {}: {}", id, e.getMessage(), e);
            return null;
        }
    }
}
