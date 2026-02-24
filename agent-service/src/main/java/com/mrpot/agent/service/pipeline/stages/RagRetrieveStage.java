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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
                
                // Build enriched document results with metadata
                int limit = Math.min(5, docs.size());
                List<Map<String, Object>> enrichedResults = buildEnrichedResults(docs, hits);
                
                var metadata = StageNames.getMetadata(StageNames.RAG);
                double avgScore = hits.isEmpty() ? 0.0 :
                    hits.stream()
                        .mapToDouble(KbHit::score)
                        .average()
                        .orElse(0.0);

                // Create SSE envelope with enhanced search results
                return new SseEnvelope(
                    StageNames.RAG,
                    String.format("%s %s... Found %d documents", 
                        metadata.emoji(), metadata.displayName(), hitCount),
                    Map.ofEntries(
                        // UI Metadata
                        Map.entry("uiComponent", metadata.uiComponent()),
                        Map.entry("displayName", metadata.displayName()),
                        Map.entry("description", metadata.description()),
                        
                        // Search Results Summary
                        Map.entry("searchResults", Map.of(
                            "status", hitCount > 0 ? "success" : "no_results",
                            "totalDocumentsFound", hitCount,
                            "documentsDisplayed", Math.min(limit, hitCount),
                            "topRelevanceScore", topScore,
                            "averageRelevanceScore", Math.round(avgScore * 1000.0) / 1000.0
                        )),
                        
                        // Detailed document results
                        Map.entry("documents", enrichedResults),
                        
                        // Search quality metrics
                        Map.entry("searchQuality", Map.of(
                            "relevanceThreshold", minScore,
                            "thresholdMet", topScore >= minScore,
                            "searchDurationMs", System.currentTimeMillis() - System.currentTimeMillis(),
                            "confidence", topScore >= 0.9 ? "high" : 
                                         topScore >= 0.7 ? "medium" : "low"
                        )),
                        
                        // Data coverage indicators
                        Map.entry("coverage", Map.of(
                            "hasTitleInfo", docs.stream().anyMatch(d -> d.title() != null),
                            "hasContentInfo", docs.stream().anyMatch(d -> d.content() != null),
                            "hasMetadataInfo", docs.stream().anyMatch(d -> d.metadata() != null)
                        ))
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
    
    
    /**
     * Build enriched document results with detailed relevance metadata.
     * Limits to first 5 documents and includes rank, relevance scores, content preview, and metadata.
     */
    private List<Map<String, Object>> buildEnrichedResults(
            List<KbDocument> docs, 
            List<KbHit> hits) {
        return IntStream.range(0, Math.min(5, docs.size()))
            .mapToObj(i -> {
                KbDocument doc = docs.get(i);
                KbHit hit = i < hits.size() ? hits.get(i) : null;
                double score = hit != null ? hit.score() : 0.0;
                String content = doc.content() != null ? doc.content() : "";
                String preview = content.length() > 200
                    ? content.substring(0, 200) + "…"
                    : content;
                String docType = doc.docType() != null ? doc.docType() : "Unknown";
                
                return Map.of(
                    "id", doc.id() != null ? doc.id() : "doc-" + (i + 1),
                    "rank", i + 1,
                    "title", doc.title() != null ? doc.title() : "(Untitled)",
                    "source", docType,
                    "preview", preview,
                    "relevance", Map.of(
                        "score", Math.round(score * 1000.0) / 1000.0,
                        "percentage", Math.round(score * 100),
                        "label", score >= 0.9 ? "Highly Relevant" :
                                score >= 0.7 ? "Relevant" :
                                score >= 0.5 ? "Moderately Relevant" : "Weakly Relevant"
                    ),
                    "metadata", Map.of(
                        "length", content.length(),
                        "hasMetadata", doc.metadata() != null,
                        "language", "en"
                    )
                );
            })
            .collect(Collectors.toList());
    }
    
    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
