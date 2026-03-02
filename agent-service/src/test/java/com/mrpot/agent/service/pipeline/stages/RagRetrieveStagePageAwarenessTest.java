package com.mrpot.agent.service.pipeline.stages;

import com.mrpot.agent.common.api.RagAnswerRequest;
import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.kb.KbDocument;
import com.mrpot.agent.common.kb.KbHit;
import com.mrpot.agent.common.kb.KbSearchRequest;
import com.mrpot.agent.common.kb.KbSearchResponse;
import com.mrpot.agent.common.policy.ExecutionPolicy;
import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.service.KbRetrievalService;
import com.mrpot.agent.service.pipeline.PipelineContext;
import com.mrpot.agent.service.telemetry.RunLogPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Test suite for RagRetrieveStage with Page-Awareness feature
 */
class RagRetrieveStagePageAwarenessTest {

    private KbRetrievalService kbRetrievalService;
    private RunLogPublisher runLogPublisher;
    private RagRetrieveStage ragRetrieveStage;
    private PipelineContext context;

    @BeforeEach
    void setUp() {
        kbRetrievalService = Mockito.mock(KbRetrievalService.class);
        runLogPublisher = Mockito.mock(RunLogPublisher.class);
        ragRetrieveStage = new RagRetrieveStage(kbRetrievalService, runLogPublisher);
        
        RagAnswerRequest request = Mockito.mock(RagAnswerRequest.class);
        ExecutionPolicy policy = Mockito.mock(ExecutionPolicy.class);
        
        when(request.question()).thenReturn("Tell me about this project");
        when(request.resolveTopK()).thenReturn(5);
        when(request.resolveMinScore()).thenReturn(0.3);
        
        context = new PipelineContext(
            "run-test-001",
            "trace-test-001",
            "session-test",
            "user-test",
            request,
            ScopeMode.OWNER_ONLY,
            policy,
            "FAST"
        );
    }

    @Test
    void process_computesPageRelevance_whenPageContextProvided() {
        // Given: Request with page context
        Map<String, Object> ext = new HashMap<>();
        ext.put("currentPagePattern", "/work-single/[id]");
        ext.put("currentPageUrl", "https://yuqi.site/work-single/abc123");
        ext.put("pageContextText", "Market Weather Dashboard project...");
        
        when(context.request().ext()).thenReturn(ext);

        // Given: KB documents with matching metadata
        Map<String, Object> doc1Meta = new HashMap<>();
        doc1Meta.put("pagePattern", "/work-single/[id]");
        doc1Meta.put("projectId", "abc123");
        
        Map<String, Object> doc2Meta = new HashMap<>();
        doc2Meta.put("pagePattern", "/blog");
        
        KbDocument doc1 = new KbDocument(
            "doc-1",
            "project",
            "Market Weather Dashboard",
            "This is a financial analytics platform...",
            doc1Meta
        );
        
        KbDocument doc2 = new KbDocument(
            "doc-2",
            "blog",
            "Blog Post",
            "Another article...",
            doc2Meta
        );
        
        List<KbDocument> docs = List.of(doc1, doc2);
        List<KbHit> hits = List.of(
            new KbHit("doc-1", 0.88),
            new KbHit("doc-2", 0.65)
        );
        
        String ragContext = "Market Weather Dashboard\nThis is a financial analytics platform...";
        
        KbSearchResponse response = new KbSearchResponse(docs, hits, ragContext, null);
        when(kbRetrievalService.searchSimilar(any(KbSearchRequest.class)))
            .thenReturn(Mono.just(response));

        // When: Process stage
        StepVerifier.create(ragRetrieveStage.process(null, context))
            .assertNext(envelope -> {
                assertEquals(StageNames.RAG, envelope.stage());
                
                // Verify page relevance is computed and stored
                Map<String, Object> pageRelevance = context.getPageRelevance();
                assertNotNull(pageRelevance, "Page relevance should be computed");
                
                assertEquals(true, pageRelevance.get("isRelevant"), "Should be relevant");
                assertEquals(0.88, pageRelevance.get("score"), "Should have correct top score");
                assertEquals("/work-single/[id]", pageRelevance.get("pagePattern"));
                
                @SuppressWarnings("unchecked")
                List<String> matchedDocIds = (List<String>) pageRelevance.get("matchedDocIds");
                assertEquals(1, matchedDocIds.size());
                assertEquals("doc-1", matchedDocIds.get(0));
            })
            .verifyComplete();
    }

    @Test
    void process_doesNotComputePageRelevance_whenNoPageContext() {
        // Given: No page context in request
        when(context.request().ext()).thenReturn(null);

        // Given: KB documents
        KbDocument doc = new KbDocument(
            "doc-1",
            "generic",
            "Some Document",
            "Content...",
            null
        );
        
        List<KbDocument> docs = List.of(doc);
        List<KbHit> hits = List.of(new KbHit("doc-1", 0.75));
        
        KbSearchResponse response = new KbSearchResponse(docs, hits, "Content...", null);
        when(kbRetrievalService.searchSimilar(any(KbSearchRequest.class)))
            .thenReturn(Mono.just(response));

        // When: Process stage
        StepVerifier.create(ragRetrieveStage.process(null, context))
            .assertNext(envelope -> {
                assertEquals(StageNames.RAG, envelope.stage());
                
                // Verify no page relevance computed
                Map<String, Object> pageRelevance = context.getPageRelevance();
                assertNull(pageRelevance, "Page relevance should not be computed without page context");
            })
            .verifyComplete();
    }

    @Test
    void process_marksNotRelevant_whenNoDocumentsMatch() {
        // Given: Page context for different pattern
        Map<String, Object> ext = new HashMap<>();
        ext.put("currentPagePattern", "/blog");
        
        when(context.request().ext()).thenReturn(ext);

        // Given: Documents with different pattern
        Map<String, Object> meta = new HashMap<>();
        meta.put("pagePattern", "/work-single/[id]");
        
        KbDocument doc = new KbDocument(
            "doc-1",
            "project",
            "Project",
            "Content...",
            meta
        );
        
        List<KbDocument> docs = List.of(doc);
        List<KbHit> hits = List.of(new KbHit("doc-1", 0.85));
        
        KbSearchResponse response = new KbSearchResponse(docs, hits, "Content...", null);
        when(kbRetrievalService.searchSimilar(any(KbSearchRequest.class)))
            .thenReturn(Mono.just(response));

        // When: Process stage
        StepVerifier.create(ragRetrieveStage.process(null, context))
            .assertNext(envelope -> {
                Map<String, Object> pageRelevance = context.getPageRelevance();
                assertNotNull(pageRelevance);
                
                assertEquals(false, pageRelevance.get("isRelevant"), "Should not be relevant");
                
                @SuppressWarnings("unchecked")
                List<String> matchedDocIds = (List<String>) pageRelevance.get("matchedDocIds");
                assertTrue(matchedDocIds.isEmpty());
            })
            .verifyComplete();
    }

    @Test
    void process_marksNotRelevant_whenScoreBelowThreshold() {
        // Given: Page context
        Map<String, Object> ext = new HashMap<>();
        ext.put("currentPagePattern", "/work-single/[id]");
        
        when(context.request().ext()).thenReturn(ext);

        // Given: Document with matching pattern but low score
        Map<String, Object> meta = new HashMap<>();
        meta.put("pagePattern", "/work-single/[id]");
        
        KbDocument doc = new KbDocument(
            "doc-low-score",
            "project",
            "Low Score Project",
            "Content...",
            meta
        );
        
        List<KbDocument> docs = List.of(doc);
        List<KbHit> hits = List.of(new KbHit("doc-low-score", 0.45)); // Below 0.55 threshold
        
        KbSearchResponse response = new KbSearchResponse(docs, hits, "Content...", null);
        when(kbRetrievalService.searchSimilar(any(KbSearchRequest.class)))
            .thenReturn(Mono.just(response));

        // When: Process stage
        StepVerifier.create(ragRetrieveStage.process(null, context))
            .assertNext(envelope -> {
                Map<String, Object> pageRelevance = context.getPageRelevance();
                assertNotNull(pageRelevance);
                
                // Should not be relevant due to low score
                assertEquals(false, pageRelevance.get("isRelevant"));
                
                @SuppressWarnings("unchecked")
                List<String> matchedDocIds = (List<String>) pageRelevance.get("matchedDocIds");
                assertTrue(matchedDocIds.isEmpty());
            })
            .verifyComplete();
    }

    @Test
    void process_handlesMultipleMatchingDocuments() {
        // Given: Page context
        Map<String, Object> ext = new HashMap<>();
        ext.put("currentPagePattern", "/work-single/[id]");
        
        when(context.request().ext()).thenReturn(ext);

        // Given: Multiple documents matching the pattern
        Map<String, Object> meta1 = new HashMap<>();
        meta1.put("pagePattern", "/work-single/[id]");
        
        Map<String, Object> meta2 = new HashMap<>();
        meta2.put("pagePattern", "/work-single/[id]");
        
        List<KbDocument> docs = List.of(
            new KbDocument("doc-1", "project", "Project 1", "Content 1", meta1),
            new KbDocument("doc-2", "project", "Project 2", "Content 2", meta2)
        );
        
        List<KbHit> hits = List.of(
            new KbHit("doc-1", 0.90),
            new KbHit("doc-2", 0.75)
        );
        
        KbSearchResponse response = new KbSearchResponse(docs, hits, "Content 1\nContent 2", null);
        when(kbRetrievalService.searchSimilar(any(KbSearchRequest.class)))
            .thenReturn(Mono.just(response));

        // When: Process stage
        StepVerifier.create(ragRetrieveStage.process(null, context))
            .assertNext(envelope -> {
                Map<String, Object> pageRelevance = context.getPageRelevance();
                assertNotNull(pageRelevance);
                
                assertEquals(true, pageRelevance.get("isRelevant"));
                assertEquals(0.90, pageRelevance.get("score"), "Should use highest score");
                
                @SuppressWarnings("unchecked")
                List<String> matchedDocIds = (List<String>) pageRelevance.get("matchedDocIds");
                assertEquals(2, matchedDocIds.size());
                assertTrue(matchedDocIds.contains("doc-1"));
                assertTrue(matchedDocIds.contains("doc-2"));
            })
            .verifyComplete();
    }
}
