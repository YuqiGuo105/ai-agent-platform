package com.mrpot.agent.knowledge.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight chunker: splits text into overlapping character windows sized for embeddings.
 */
@Service
public class DocumentChunkingService {

    private static final int DEFAULT_CHUNK_SIZE = 1200;
    private static final int DEFAULT_OVERLAP = 200;

    public List<String> chunk(String text) {
        return chunk(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public List<String> chunk(String text, int chunkSize, int overlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        int safeChunk = Math.max(200, chunkSize);
        int safeOverlap = Math.max(0, Math.min(overlap, safeChunk / 2));

        String normalized = text.trim();
        int len = normalized.length();
        if (len <= safeChunk) {
            return List.of(normalized);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < len) {
            int end = Math.min(start + safeChunk, len);
            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (end >= len) {
                break;
            }
            start = Math.max(0, end - safeOverlap);
        }
        return chunks;
    }
}
