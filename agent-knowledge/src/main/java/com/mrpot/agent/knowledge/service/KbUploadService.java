package com.mrpot.agent.knowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrpot.agent.common.tool.FileUnderstanding;
import com.mrpot.agent.knowledge.model.ChunkPreview;
import com.mrpot.agent.knowledge.model.ChunkPreviewResponse;
import com.mrpot.agent.knowledge.model.SaveChunksRequest;
import com.mrpot.agent.knowledge.model.TextUploadRequest;
import com.mrpot.agent.knowledge.model.UploadRequest;
import com.mrpot.agent.knowledge.repository.KbDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KbUploadService {

    private final FileUnderstandingClient fileUnderstandingClient;
    private final DocumentChunkingService documentChunkingService;
    private final EmbeddingService embeddingService;
    private final KbDocumentRepository repository;
    private final S3StorageService s3StorageService;
    private final ObjectMapper objectMapper;

    public ChunkPreviewResponse processFileUpload(UploadRequest request) {
        if (!StringUtils.hasText(request.fileUrl())) {
            throw new IllegalArgumentException("fileUrl is required");
        }

        // Generate presigned download URL for private S3 bucket access
        String accessUrl;
        try {
            var presigned = s3StorageService.presignDownloadByUrl(request.fileUrl());
            accessUrl = presigned.downloadUrl();
            log.debug("Generated presigned download URL for file extraction");
        } catch (Exception e) {
            log.warn("Could not generate presigned URL, using original URL: {}", e.getMessage());
            accessUrl = request.fileUrl();
        }

        FileUnderstanding understanding = fileUnderstandingClient.understandUrl(accessUrl);
        if (!understanding.isSuccess()) {
            throw new IllegalStateException("File extraction failed: " + understanding.error());
        }

        String text = understanding.text();
        String docType = detectDocType(request.fileUrl());

        ChunkPreviewResponse response = chunkAndEmbed(text, docType, request.metadata());

        // Attempt cleanup after processing
        s3StorageService.deleteByUrl(request.fileUrl());

        return response;
    }

    public ChunkPreviewResponse processTextUpload(TextUploadRequest request) {
        if (!StringUtils.hasText(request.text())) {
            throw new IllegalArgumentException("text is required");
        }
        String docType = "text";
        return chunkAndEmbed(request.text(), docType, request.metadata());
    }

    public List<Long> saveChunks(SaveChunksRequest request) {
        if (request.chunks() == null || request.chunks().isEmpty()) {
            return List.of();
        }
        String docType = StringUtils.hasText(request.docType()) ? request.docType() : "text";
        List<Long> ids = new ArrayList<>();
        for (SaveChunksRequest.Chunk chunk : request.chunks()) {
            Map<String, Object> mergedMeta = new HashMap<>();
            if (request.metadata() != null) {
                mergedMeta.putAll(request.metadata());
            }
            if (chunk.metadata() != null) {
                mergedMeta.putAll(chunk.metadata());
            }
            mergedMeta.putIfAbsent("chunkIndex", chunk.index());
            long id = repository.insert(docType, chunk.content(), mergedMeta, chunk.embedding());
            ids.add(id);
        }
        return ids;
    }

    private ChunkPreviewResponse chunkAndEmbed(String text, String docType, Map<String, Object> metadata) {
        List<String> chunks = documentChunkingService.chunk(text);
        List<ChunkPreview> previews = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            float[] embedding = embeddingService.embed(chunk);
            previews.add(new ChunkPreview(i, chunk, chunk.length(), embedding));
        }
        return new ChunkPreviewResponse(docType, previews, metadata);
    }

    private String detectDocType(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return "unknown";
        }
        String lower = fileUrl.toLowerCase();
        if (lower.contains(".pdf")) return "pdf";
        if (lower.contains(".docx")) return "docx";
        if (lower.contains(".doc")) return "doc";
        if (lower.contains(".txt")) return "txt";
        return "unknown";
    }
}
