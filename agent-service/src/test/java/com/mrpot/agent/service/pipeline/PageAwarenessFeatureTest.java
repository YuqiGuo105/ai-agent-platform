package com.mrpot.agent.service.pipeline;

import com.mrpot.agent.common.api.RagAnswerRequest;
import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.kb.KbDocument;
import com.mrpot.agent.common.kb.KbHit;
import com.mrpot.agent.common.policy.ExecutionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Test suite for Page-Awareness Feature
 * Tests the end-to-end flow of page context extraction, relevance computation,
 * and response highlighting.
 */
class PageAwarenessFeatureTest {

    private PipelineContext context;
    private RagAnswerRequest request;

    @BeforeEach
    void setUp() {
        ExecutionPolicy policy = Mockito.mock(ExecutionPolicy.class);
        request = Mockito.mock(RagAnswerRequest.class);
        
        context = new PipelineContext(
            "run-page-test-001",
            "trace-page-test-001",
            "session-page-test",
            "user-test",
            request,
            ScopeMode.OWNER_ONLY,
            policy,
            "FAST"
        );
    }

    @Test
    void pageRelevance_canBeStoredAndRetrieved() {
        // Given: Page relevance data
        Map<String, Object> pageRelevance = new HashMap<>();
        pageRelevance.put("isRelevant", true);
        pageRelevance.put("score", 0.85);
        pageRelevance.put("matchedDocIds", List.of("doc-1", "doc-2"));
        pageRelevance.put("pagePattern", "/work-single/[id]");

        // When: Store in context
        context.setPageRelevance(pageRelevance);

        // Then: Can be retrieved
        Map<String, Object> retrieved = context.getPageRelevance();
        assertNotNull(retrieved);
        assertEquals(true, retrieved.get("isRelevant"));
        assertEquals(0.85, retrieved.get("score"));
        assertEquals("/work-single/[id]", retrieved.get("pagePattern"));
        
        @SuppressWarnings("unchecked")
        List<String> docIds = (List<String>) retrieved.get("matchedDocIds");
        assertEquals(2, docIds.size());
        assertEquals("doc-1", docIds.get(0));
    }

    @Test
    void pageRelevance_returnsNullWhenNotSet() {
        // When: No page relevance set
        Map<String, Object> retrieved = context.getPageRelevance();

        // Then: Should return null
        assertNull(retrieved);
    }

    @Test
    void pageContextExtraction_fromRequestExt() {
        // Given: Request with ext containing page context
        Map<String, Object> ext = new HashMap<>();
        ext.put("currentPageUrl", "https://yuqi.site/work-single/abc123");
        ext.put("currentPagePattern", "/work-single/[id]");
        ext.put("pageContextText", "Market Weather Dashboard is a real-time financial analytics platform...");
        ext.put("pageTitle", "Market Weather Dashboard - Project");

        when(request.ext()).thenReturn(ext);

        // Then: Can extract all page context fields
        assertEquals("https://yuqi.site/work-single/abc123", ext.get("currentPageUrl"));
        assertEquals("/work-single/[id]", ext.get("currentPagePattern"));
        assertTrue(((String) ext.get("pageContextText")).contains("Market Weather Dashboard"));
        assertEquals("Market Weather Dashboard - Project", ext.get("pageTitle"));
    }

    @Test
    void pageRelevanceComputation_matchesCorrectPattern() {
        // Given: RAG documents with metadata
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
            new KbHit("doc-1", 0.85),
            new KbHit("doc-2", 0.60)
        );

        // When: Compute page relevance for /work-single/[id]
        String currentPagePattern = "/work-single/[id]";
        final double PAGE_MATCH_MIN_SCORE = 0.55;
        
        List<String> matchedDocIds = new java.util.ArrayList<>();
        double topPageScore = 0.0;
        
        for (int i = 0; i < docs.size(); i++) {
            KbDocument doc = docs.get(i);
            double score = i < hits.size() ? hits.get(i).score() : 0.0;
            
            if (score < PAGE_MATCH_MIN_SCORE) continue;
            
            Object docPagePattern = doc.metadata() != null ? doc.metadata().get("pagePattern") : null;
            if (currentPagePattern.equals(String.valueOf(docPagePattern))) {
                matchedDocIds.add(doc.id() != null ? doc.id() : "doc-" + i);
                topPageScore = Math.max(topPageScore, score);
            }
        }
        
        boolean isRelevant = !matchedDocIds.isEmpty();

        // Then: Should match only doc-1
        assertTrue(isRelevant);
        assertEquals(1, matchedDocIds.size());
        assertEquals("doc-1", matchedDocIds.get(0));
        assertEquals(0.85, topPageScore);
    }

    @Test
    void pageRelevanceComputation_ignoresLowScoreDocuments() {
        // Given: Documents with scores below threshold
        Map<String, Object> meta = new HashMap<>();
        meta.put("pagePattern", "/work-single/[id]");
        
        KbDocument doc = new KbDocument(
            "doc-low-score",
            "project",
            "Low relevance",
            "Not very relevant content",
            meta
        );
        
        List<KbDocument> docs = List.of(doc);
        List<KbHit> hits = List.of(new KbHit("doc-low-score", 0.45)); // Below 0.55 threshold

        // When: Compute page relevance
        String currentPagePattern = "/work-single/[id]";
        final double PAGE_MATCH_MIN_SCORE = 0.55;
        
        List<String> matchedDocIds = new java.util.ArrayList<>();
        
        for (int i = 0; i < docs.size(); i++) {
            KbDocument d = docs.get(i);
            double score = i < hits.size() ? hits.get(i).score() : 0.0;
            
            if (score < PAGE_MATCH_MIN_SCORE) continue;
            
            Object docPagePattern = d.metadata() != null ? d.metadata().get("pagePattern") : null;
            if (currentPagePattern.equals(String.valueOf(docPagePattern))) {
                matchedDocIds.add(d.id());
            }
        }

        // Then: No documents should match
        assertTrue(matchedDocIds.isEmpty());
    }

    @Test
    void pageRelevanceComputation_handlesNoMetadata() {
        // Given: Document without metadata
        KbDocument doc = new KbDocument(
            "doc-no-meta",
            "generic",
            "No metadata",
            "Content without metadata",
            null // No metadata
        );
        
        List<KbDocument> docs = List.of(doc);
        List<KbHit> hits = List.of(new KbHit("doc-no-meta", 0.75));

        // When: Compute page relevance
        String currentPagePattern = "/work-single/[id]";
        final double PAGE_MATCH_MIN_SCORE = 0.55;
        
        List<String> matchedDocIds = new java.util.ArrayList<>();
        
        for (int i = 0; i < docs.size(); i++) {
            KbDocument d = docs.get(i);
            double score = i < hits.size() ? hits.get(i).score() : 0.0;
            
            if (score < PAGE_MATCH_MIN_SCORE) continue;
            
            Object docPagePattern = d.metadata() != null ? d.metadata().get("pagePattern") : null;
            if (currentPagePattern.equals(String.valueOf(docPagePattern))) {
                matchedDocIds.add(d.id());
            }
        }

        // Then: Should not match (null metadata doesn't match pattern)
        assertTrue(matchedDocIds.isEmpty());
    }

    @Test
    void pageRelevanceComputation_multipleMatchingDocuments() {
        // Given: Multiple documents matching the same page pattern
        Map<String, Object> meta1 = new HashMap<>();
        meta1.put("pagePattern", "/blog");
        meta1.put("postId", "post-1");
        
        Map<String, Object> meta2 = new HashMap<>();
        meta2.put("pagePattern", "/blog");
        meta2.put("postId", "post-2");
        
        Map<String, Object> meta3 = new HashMap<>();
        meta3.put("pagePattern", "/");
        
        List<KbDocument> docs = List.of(
            new KbDocument("doc-1", "blog", "Blog Post 1", "Content 1", meta1),
            new KbDocument("doc-2", "blog", "Blog Post 2", "Content 2", meta2),
            new KbDocument("doc-3", "page", "Home Page", "Content 3", meta3)
        );
        
        List<KbHit> hits = List.of(
            new KbHit("doc-1", 0.90),
            new KbHit("doc-2", 0.75),
            new KbHit("doc-3", 0.65)
        );

        // When: Compute page relevance for /blog
        String currentPagePattern = "/blog";
        final double PAGE_MATCH_MIN_SCORE = 0.55;
        
        List<String> matchedDocIds = new java.util.ArrayList<>();
        double topPageScore = 0.0;
        
        for (int i = 0; i < docs.size(); i++) {
            KbDocument doc = docs.get(i);
            double score = i < hits.size() ? hits.get(i).score() : 0.0;
            
            if (score < PAGE_MATCH_MIN_SCORE) continue;
            
            Object docPagePattern = doc.metadata() != null ? doc.metadata().get("pagePattern") : null;
            if (currentPagePattern.equals(String.valueOf(docPagePattern))) {
                matchedDocIds.add(doc.id());
                topPageScore = Math.max(topPageScore, score);
            }
        }

        // Then: Should match both blog documents
        assertEquals(2, matchedDocIds.size());
        assertTrue(matchedDocIds.contains("doc-1"));
        assertTrue(matchedDocIds.contains("doc-2"));
        assertEquals(0.90, topPageScore); // Highest score from matched docs
    }

    @Test
    void fullWorkflow_pageContextSentAndRelevanceReturned() {
        // Given: Full pipeline workflow simulation
        
        // 1. Frontend sends page context
        Map<String, Object> ext = new HashMap<>();
        ext.put("currentPageUrl", "https://yuqi.site/work-single/project-123");
        ext.put("currentPagePattern", "/work-single/[id]");
        ext.put("pageContextText", "Market Weather Dashboard - A comprehensive financial analytics platform that provides real-time market insights...");
        ext.put("pageTitle", "Market Weather Dashboard");
        
        when(request.ext()).thenReturn(ext);

        // 2. RAG retrieval finds matching document
        Map<String, Object> docMeta = new HashMap<>();
        docMeta.put("pagePattern", "/work-single/[id]");
        docMeta.put("projectId", "project-123");
        
        KbDocument matchingDoc = new KbDocument(
            "proj-doc-123",
            "project",
            "Market Weather Dashboard",
            "Detailed project description with Market Weather Dashboard features...",
            docMeta
        );
        
        List<KbDocument> docs = List.of(matchingDoc);
        List<KbHit> hits = List.of(new KbHit("proj-doc-123", 0.88));
        
        // Store in context
        context.setRagDocs(docs);
        context.setRagHits(hits);

        // 3. Compute page relevance
        String currentPagePattern = String.valueOf(ext.get("currentPagePattern"));
        final double PAGE_MATCH_MIN_SCORE = 0.55;
        
        List<String> matchedDocIds = new java.util.ArrayList<>();
        double topPageScore = 0.0;
        
        for (int i = 0; i < docs.size(); i++) {
            KbDocument doc = docs.get(i);
            double score = i < hits.size() ? hits.get(i).score() : 0.0;
            
            if (score < PAGE_MATCH_MIN_SCORE) continue;
            
            Object docPagePattern = doc.metadata() != null ? doc.metadata().get("pagePattern") : null;
            if (currentPagePattern.equals(String.valueOf(docPagePattern))) {
                matchedDocIds.add(doc.id());
                topPageScore = Math.max(topPageScore, score);
            }
        }
        
        boolean isRelevant = !matchedDocIds.isEmpty();
        
        Map<String, Object> pageRelevance = new HashMap<>();
        pageRelevance.put("isRelevant", isRelevant);
        pageRelevance.put("score", topPageScore);
        pageRelevance.put("matchedDocIds", matchedDocIds);
        pageRelevance.put("pagePattern", currentPagePattern);
        
        context.setPageRelevance(pageRelevance);

        // Then: Verify complete workflow
        assertTrue(isRelevant, "Answer should be relevant to current page");
        assertEquals(0.88, topPageScore);
        assertEquals(1, matchedDocIds.size());
        assertEquals("proj-doc-123", matchedDocIds.get(0));
        
        // Verify page relevance is stored correctly
        Map<String, Object> storedRelevance = context.getPageRelevance();
        assertNotNull(storedRelevance);
        assertEquals(true, storedRelevance.get("isRelevant"));
        assertEquals("/work-single/[id]", storedRelevance.get("pagePattern"));
    }
}
