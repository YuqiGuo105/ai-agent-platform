package com.mrpot.agent.service;

import com.mrpot.agent.common.kb.KbDocument;
import com.mrpot.agent.common.kb.KbHit;
import com.mrpot.agent.common.kb.KbSearchRequest;
import com.mrpot.agent.common.kb.KbSearchResponse;
import com.mrpot.agent.common.kb.ScoredDocument;
import com.mrpot.agent.repository.KbDocumentVectorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for retrieving documents from the knowledge base using semantic search.
 * Converts text queries to embeddings and searches the pgvector database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbRetrievalService {

    private final EmbeddingModel embeddingModel;
    private final KbDocumentVectorRepository repository;

    /**
     * Search for similar documents using semantic similarity.
     *
     * @param request search request with query, topK, minScore, and optional filters
     * @return Mono with search response containing documents, hits, and context text
     */
    public Mono<KbSearchResponse> searchSimilar(KbSearchRequest request) {
        log.info("Searching KB with query: '{}', topK: {}, minScore: {}",
                request.query(), request.topK(), request.minScore());

        // Convert query to embedding asynchronously (on boundedElastic scheduler)
        return convertToEmbedding(request.query())
                .map(embedding -> {
                    // Query vector database
                    List<ScoredDocument> scoredDocs = repository.findNearest(embedding, request.topK());

                    // Filter by minimum score
                    List<ScoredDocument> filtered = scoredDocs.stream()
                            .filter(sd -> sd.score() >= request.minScore())
                            .toList();

                    log.info("Found {} documents (after filtering by minScore >= {})", filtered.size(), request.minScore());

                    // Transform to response
                    List<KbDocument> docs = filtered.stream()
                            .map(ScoredDocument::document)
                            .toList();

                    List<KbHit> hits = filtered.stream()
                            .map(sd -> new KbHit(sd.document().id(), sd.score()))
                            .toList();

                    // Build context text from document contents
                    String contextText = filtered.stream()
                            .map(sd -> sd.document().content())
                            .filter(content -> content != null && !content.isBlank())
                            .collect(Collectors.joining("\n\n"));

                    Long sourceTs = Instant.now().toEpochMilli();

                    return new KbSearchResponse(docs, hits, contextText, sourceTs);
                })
                .onErrorResume(e -> {
                    log.error("Error searching KB: {}", e.getMessage(), e);
                    // Return empty response on error
                    return Mono.just(new KbSearchResponse(
                            List.of(),
                            List.of(),
                            "",
                            Instant.now().toEpochMilli()
                    ));
                });
    }

    /**
     * Get a document by its ID.
     *
     * @param id the document ID
     * @return the document if found, null otherwise
     */
    public KbDocument getDocumentById(String id) {
        log.info("Getting KB document by ID: {}", id);
        try {
            Long longId = Long.parseLong(id);
            return repository.findById(longId);
        } catch (NumberFormatException e) {
            log.error("Invalid document ID format: {}", id);
            return null;
        } catch (Exception e) {
            log.error("Error getting document by ID: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Convert text to embedding vector using the configured embedding model.
     * Runs the blocking OpenAI API call on a separate scheduler to avoid blocking Netty event loop.
     *
     * @param text the text to convert
     * @return Mono with embedding vector as float array
     */
    private Mono<float[]> convertToEmbedding(String text) {
        // Use Mono.fromCallable to wrap the blocking operation
        // subscribeOn(Schedulers.boundedElastic()) offloads to a thread pool for blocking I/O
        return Mono.fromCallable(() -> {
            EmbeddingRequest embeddingRequest = new EmbeddingRequest(
                    List.of(text),
                    null // options can be null, uses configured defaults
            );

            EmbeddingResponse response = embeddingModel.call(embeddingRequest);

            if (response.getResults().isEmpty()) {
                throw new RuntimeException("Embedding model returned no results for query");
            }

            // Get the embedding from the first result
            // Spring AI returns float[] directly from getOutput()
            return response.getResults().get(0).getOutput();
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
