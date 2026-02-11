package com.mrpot.agent.service.pipeline.stages;

import com.mrpot.agent.common.kb.KbSearchRequest;
import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.service.KbRetrievalService;
import com.mrpot.agent.service.pipeline.PipelineContext;
import com.mrpot.agent.service.pipeline.Processor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Pipeline stage for RAG (Retrieval-Augmented Generation) retrieval.
 * Searches the knowledge base and stores context for LLM prompt augmentation.
 */
@Slf4j
@RequiredArgsConstructor
public class RagRetrieveStage implements Processor<Void, SseEnvelope> {
    
    private final KbRetrievalService kbRetrievalService;
    
    @Override
    public Mono<SseEnvelope> process(Void input, PipelineContext context) {
        String query = context.request().question();
        int topK = context.request().resolveTopK();
        double minScore = context.request().resolveMinScore();
        
        log.info("RAG retrieval: query='{}', topK={}, minScore={} for runId={}",
            truncate(query, 50), topK, minScore, context.runId());
        
        KbSearchRequest searchRequest = new KbSearchRequest(
            query,
            topK,
            minScore,
            null  // No document filters
        );
        
        return kbRetrievalService.searchSimilar(searchRequest)
            .map(response -> {
                // Store RAG context, documents, and hits in pipeline context
                String ragContext = response.contextText();
                context.setRagContext(ragContext);
                context.setRagDocs(response.docs());
                context.setRagHits(response.hits());
                
                int hitCount = response.hits().size();
                double topScore = response.hits().isEmpty() ? 0.0 : response.hits().get(0).score();
                int contextLength = ragContext != null ? ragContext.length() : 0;
                
                log.info("RAG retrieval complete: {} hits, topScore={}, contextLength={} for runId={}",
                    hitCount, topScore, contextLength, context.runId());
                
                // Create SSE envelope with RAG results
                return new SseEnvelope(
                    StageNames.RAG,
                    "Knowledge base search complete",
                    Map.of(
                        "hitCount", hitCount,
                        "topScore", topScore,
                        "contextLength", contextLength
                    ),
                    context.nextSeq(),
                    System.currentTimeMillis(),
                    context.traceId(),
                    context.sessionId()
                );
            })
            .onErrorResume(e -> {
                log.error("RAG retrieval failed for runId={}: {}", context.runId(), e.getMessage(), e);
                
                // Store empty context on error
                context.setRagContext("");
                
                // Return error envelope
                return Mono.just(new SseEnvelope(
                    StageNames.ERROR,
                    "RAG retrieval failed: " + e.getMessage(),
                    Map.of(
                        "stage", "rag",
                        "error", e.getClass().getSimpleName(),
                        "message", e.getMessage() != null ? e.getMessage() : "Unknown error"
                    ),
                    context.nextSeq(),
                    System.currentTimeMillis(),
                    context.traceId(),
                    context.sessionId()
                ));
            });
    }
    
    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
