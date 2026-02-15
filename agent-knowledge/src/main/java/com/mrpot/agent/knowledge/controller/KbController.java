package com.mrpot.agent.knowledge.controller;

import com.mrpot.agent.common.kb.KbDocument;
import com.mrpot.agent.knowledge.model.*;
import com.mrpot.agent.knowledge.service.KbManagementService;
import com.mrpot.agent.knowledge.service.KbUploadService;
import com.mrpot.agent.knowledge.service.S3StorageService;
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
    private final KbUploadService kbUploadService;
    private final S3StorageService s3StorageService;

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

    @GetMapping("/documents/search")
    @Operation(summary = "Search or list KB documents with pagination",
            description = "Returns paginated documents and total counts. If keyword is omitted, returns all documents.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated results returned")
    })
    public ResponseEntity<PagedResponse<KbDocument>> searchDocuments(
            @Parameter(description = "Optional keyword for fuzzy match")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "Optional: exact document type filter")
            @RequestParam(required = false) String docType,
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        PagedResponse<KbDocument> response = kbManagementService.searchDocuments(keyword, docType, page, size);
        return ResponseEntity.ok(response);
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

    // ─── POST /kb/upload/presign ───────────────────────────────────
    @PostMapping("/upload/presign")
    @Operation(summary = "Create presigned upload URL",
            description = "Generates a temporary S3 upload URL so the frontend can upload without exposing credentials")
    public ResponseEntity<PresignUploadResponse> presignUpload(@RequestBody PresignUploadRequest request) {
        var presigned = s3StorageService.presignUpload(request.filename(), request.contentType());
        PresignUploadResponse response = new PresignUploadResponse(
                presigned.method(),
            presigned.uploadUrl(),
                presigned.fileUrl(),
                presigned.objectKey(),
                s3StorageService.getBucket()
        );
        return ResponseEntity.ok(response);
    }

    // ─── GET /kb/download/presign ──────────────────────────────────
    @GetMapping("/download/presign")
    @Operation(summary = "Create presigned download URL",
            description = "Generates a temporary S3 download URL for accessing private objects")
    public ResponseEntity<Map<String, Object>> presignDownload(
            @Parameter(description = "S3 object key or file URL")
            @RequestParam String fileUrl) {
        try {
            var presigned = s3StorageService.presignDownloadByUrl(fileUrl);
            return ResponseEntity.ok(Map.of(
                    "downloadUrl", presigned.downloadUrl(),
                    "objectKey", presigned.objectKey(),
                    "expiresInSeconds", presigned.expiresInSeconds()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── POST /kb/upload (file URL) ────────────────────────────────
    @PostMapping("/upload")
    @Operation(summary = "Upload document and generate RAG chunks",
            description = "Accepts file URL, extracts content, chunks it, and returns chunk previews")
    public ResponseEntity<ChunkPreviewResponse> uploadDocument(@RequestBody UploadRequest request) {
        try {
            ChunkPreviewResponse response = kbUploadService.processFileUpload(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ─── POST /kb/upload/text ──────────────────────────────────────
    @PostMapping("/upload/text")
    @Operation(summary = "Upload plain text and generate RAG chunks",
            description = "Chunks provided text directly without file processing")
    public ResponseEntity<ChunkPreviewResponse> uploadText(@RequestBody TextUploadRequest request) {
        try {
            ChunkPreviewResponse response = kbUploadService.processTextUpload(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ─── POST /kb/upload/save ──────────────────────────────────────
    @PostMapping("/upload/save")
    @Operation(summary = "Persist reviewed chunks",
            description = "Saves generated chunk embeddings into kb_documents")
    public ResponseEntity<Map<String, Object>> saveChunks(@RequestBody SaveChunksRequest request) {
        List<Long> ids = kbUploadService.saveChunks(request);
        return ResponseEntity.ok(Map.of(
                "saved", ids.size(),
                "ids", ids
        ));
    }
}
