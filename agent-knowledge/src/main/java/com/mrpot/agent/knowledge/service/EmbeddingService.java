package com.mrpot.agent.knowledge.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    /**
     * Convert text to embedding using configured model.
     */
    public float[] embed(String text) {
        EmbeddingRequest request = new EmbeddingRequest(List.of(text), null);
        EmbeddingResponse response = embeddingModel.call(request);
        if (response.getResults().isEmpty()) {
            throw new IllegalStateException("Embedding model returned no results");
        }
        float[] vector = response.getResults().get(0).getOutput();
        log.debug("Generated embedding with {} dimensions", vector.length);
        return vector;
    }
}
