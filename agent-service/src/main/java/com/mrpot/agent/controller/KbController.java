package com.mrpot.agent.controller;

import com.mrpot.agent.common.kb.KbDocument;
import com.mrpot.agent.common.kb.KbSearchRequest;
import com.mrpot.agent.common.kb.KbSearchResponse;
import com.mrpot.agent.service.KbRetrievalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * REST controller for knowledge base retrieval operations.
 * Provides endpoints for semantic search and document retrieval.
 */
@RestController
@RequestMapping("/kb")
@RequiredArgsConstructor
@Tag(name = "Knowledge Base", description = "KB document retrieval and search")
public class KbController {

    private final KbRetrievalService kbRetrievalService;

    /**
     * Search the knowledge base using semantic similarity.
     *
     * @param request search request with query and parameters
     * @return Mono with search response containing matching documents
     */
    @PostMapping("/search")
    @Operation(summary = "Search KB documents", description = "Search for documents using semantic similarity")
    public Mono<ResponseEntity<KbSearchResponse>> search(@RequestBody KbSearchRequest request) {
        return kbRetrievalService.searchSimilar(request)
                .map(ResponseEntity::ok);
    }

    /**
     * Get a specific document by ID.
     *
     * @param id the document ID
     * @return the document if found
     */
    @GetMapping("/documents/{id}")
    @Operation(summary = "Get document by ID", description = "Retrieve full content of a specific document")
    public ResponseEntity<KbDocument> getDocument(@PathVariable String id) {
        KbDocument document = kbRetrievalService.getDocumentById(id);
        if (document == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(document);
    }
}
