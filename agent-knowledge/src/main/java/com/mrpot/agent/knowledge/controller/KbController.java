package com.mrpot.agent.knowledge.controller;

import com.mrpot.agent.common.kb.KbDocument;
import com.mrpot.agent.knowledge.model.FuzzySearchRequest;
import com.mrpot.agent.knowledge.service.KbManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/kb")
@RequiredArgsConstructor
@Tag(name = "Knowledge Base Management", description = "CRUD and search APIs for KB documents")
public class KbController {

    private final KbManagementService kbManagementService;

    // ─── GET /kb/documents ──────────────────────────────────────────
    @GetMapping("/documents")
    @Operation(summary = "List all KB documents",
               description = "Retrieve all documents with pagination, ordered by ID descending")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of documents returned")
    })
    public ResponseEntity<List<KbDocument>> getAllDocuments(
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size) {

        List<KbDocument> docs = kbManagementService.getAllDocuments(page, Math.min(size, 100));
        return ResponseEntity.ok(docs);
    }

    // ─── GET /kb/documents/{id} ─────────────────────────────────────
    @GetMapping("/documents/{id}")
    @Operation(summary = "Get document by ID",
               description = "Retrieve full content of a specific document")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Document found"),
        @ApiResponse(responseCode = "404", description = "Document not found")
    })
    public ResponseEntity<KbDocument> getDocument(
            @Parameter(description = "Document ID", example = "1")
            @PathVariable String id) {

        KbDocument document = kbManagementService.getDocumentById(id);
        if (document == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(document);
    }

    // ─── DELETE /kb/documents/{id} ──────────────────────────────────
    @DeleteMapping("/documents/{id}")
    @Operation(summary = "Delete KB document",
               description = "Delete a document from the knowledge base by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Document deleted"),
        @ApiResponse(responseCode = "404", description = "Document not found")
    })
    public ResponseEntity<Map<String, Object>> deleteDocument(
            @Parameter(description = "Document ID", example = "1")
            @PathVariable String id) {

        boolean deleted = kbManagementService.deleteDocument(id);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "deleted", true,
                "id", id,
                "timestamp", Instant.now().toEpochMilli()
        ));
    }

    // ─── POST /kb/search/fuzzy ──────────────────────────────────────
    @PostMapping("/search/fuzzy")
    @Operation(summary = "Fuzzy search KB documents",
               description = "Search documents using case-insensitive pattern matching on content, doc_type, and metadata")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Search results returned"),
        @ApiResponse(responseCode = "400", description = "Missing keyword")
    })
    public ResponseEntity<List<KbDocument>> fuzzySearch(@RequestBody FuzzySearchRequest request) {
        if (request.keyword() == null || request.keyword().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        List<KbDocument> results = kbManagementService.fuzzySearch(
                request.keyword(),
                request.docType(),
                request.resolvePage(),
                request.resolveSize()
        );
        return ResponseEntity.ok(results);
    }
}
