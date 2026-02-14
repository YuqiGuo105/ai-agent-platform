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

import com.mrpot.agent.common.kb.KbDocument;
import com.mrpot.agent.common.kb.KbHit;

import java.util.ArrayList;
import java.util.List;
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
                
                List<KbDocument> docs = response.docs();
                List<KbHit> hits = response.hits();
                int hitCount = hits.size();
                double topScore = hits.isEmpty() ? 0.0 : hits.get(0).score();
                int contextLength = ragContext != null ? ragContext.length() : 0;
                
                log.info("RAG retrieval complete: {} hits, topScore={}, contextLength={} for runId={}",
                    hitCount, topScore, contextLength, context.runId());
                
                // Build top results preview for frontend
                int limit = Math.min(5, docs.size());
                List<Map<String, Object>> topResults = new ArrayList<>(limit);
                for (int i = 0; i < limit; i++) {
                    KbDocument doc = docs.get(i);
                    double docScore = i < hits.size() ? hits.get(i).score() : 0.0;
                    String content = doc.content() != null ? doc.content() : "";
                    String preview = content.length() > 150
                        ? content.substring(0, 150) + "..."
                        : content;
                    topResults.add(Map.of(
                        "title", doc.title() != null ? doc.title() : "Untitled",
                        "preview", preview,
                        "score", Math.round(docScore * 100.0) / 100.0
                    ));
                }
                
                // Build display message from top result preview
                String displayMessage = topResults.isEmpty()
                    ? "Searching... no relevant docs"
                    : "Searching... found " + hitCount + " docs";

                // Create SSE envelope with RAG results and top document previews
                return new SseEnvelope(
                    StageNames.RAG,
                    displayMessage,
                    Map.of(
                        "status", "completed",
                        "foundRelevantInfo", hitCount > 0,
                        "topResults", topResults
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
