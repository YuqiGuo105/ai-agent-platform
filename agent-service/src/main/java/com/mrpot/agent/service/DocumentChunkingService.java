package com.mrpot.agent.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple text chunker for RAG embeddings.
 * Splits long text into overlapping windows measured by character length.
 */
@Service
public class DocumentChunkingService {

    private static final int DEFAULT_CHUNK_SIZE = 1200; // characters per chunk
    private static final int DEFAULT_OVERLAP = 200;     // overlap between chunks

    /**
     * Chunk text using default configuration.
     */
    public List<String> chunk(String text) {
        return chunk(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    /**
     * Chunk text using sliding window with overlap.
     * Falls back to a single chunk if the text is shorter than the window.
     */
    public List<String> chunk(String text, int chunkSize, int overlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        int safeChunk = Math.max(200, chunkSize);
        int safeOverlap = Math.max(0, Math.min(overlap, safeChunk / 2));

        String normalized = text.trim();
        int len = normalized.length();

        // Short text, no need to split
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
            if (end == len) {
                break;
            }
            // move window forward with overlap
            start = Math.max(0, end - safeOverlap);
            if (start >= len) {
                break;
            }
        }
        return chunks;
    }
}
