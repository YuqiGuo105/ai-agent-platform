package com.mrpot.agent.service;

import com.mrpot.agent.common.kb.KbDocument;
import com.mrpot.agent.common.kb.KbHit;
import com.mrpot.agent.common.kb.KbSearchRequest;
import com.mrpot.agent.common.kb.KbSearchResponse;
import com.mrpot.agent.common.kb.ScoredDocument;
import com.mrpot.agent.repository.KbDocumentVectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test for KbRetrievalService.
 * Tests search logic with mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
class KbRetrievalServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private KbDocumentVectorRepository repository;

    private KbRetrievalService service;

    private static final int EMBEDDING_DIMENSIONS = 1536;

    @BeforeEach
    void setUp() {
        service = new KbRetrievalService(embeddingModel, repository);
    }

    @Test
    void testSearchSimilar_shouldReturnFilteredResults() {
        // Given: Mock embedding model
        float[] mockEmbedding = createMockEmbedding();
        mockEmbeddingModelResponse(mockEmbedding);

        // Given: Mock repository results
        List<ScoredDocument> mockResults = List.of(
            createScoredDocument("1", "High relevance document", 0.95),
            createScoredDocument("2", "Medium relevance document", 0.75),
            createScoredDocument("3", "Low relevance document", 0.15)
        );
        when(repository.findNearest(any(float[].class), eq(5))).thenReturn(mockResults);

        // When: Search with default minScore (0.2)
        KbSearchRequest request = new KbSearchRequest("test query", null, null, null);
        KbSearchResponse response = service.searchSimilar(request).block();

        // Then: Should filter out low-scoring documents below 0.2
        assertNotNull(response);
        assertEquals(2, response.docs().size(), "Should only return docs with score >= 0.2");
        assertEquals(2, response.hits().size());

        // Verify high-scoring documents are included
        assertTrue(response.docs().stream().anyMatch(d -> d.id().equals("1")));
        assertTrue(response.docs().stream().anyMatch(d -> d.id().equals("2")));
        assertFalse(response.docs().stream().anyMatch(d -> d.id().equals("3")));

        // Verify hits contain scores
        KbHit firstHit = response.hits().get(0);
        assertEquals("1", firstHit.id());
        assertEquals(0.95, firstHit.score());
    }

    @Test
    void testSearchSimilar_withCustomTopK_shouldLimitResults() {
        // Given: Mock embedding
        mockEmbeddingModelResponse(createMockEmbedding());

        // Given: Mock repository with 5 results
        List<ScoredDocument> mockResults = List.of(
            createScoredDocument("1", "Doc 1", 0.95),
            createScoredDocument("2", "Doc 2", 0.90),
            createScoredDocument("3", "Doc 3", 0.85),
            createScoredDocument("4", "Doc 4", 0.80),
            createScoredDocument("5", "Doc 5", 0.75)
        );
        when(repository.findNearest(any(float[].class), eq(3))).thenReturn(mockResults.subList(0, 3));

        // When: Search with topK=3
        KbSearchRequest request = new KbSearchRequest("test query", 3, 0.7, null);
        KbSearchResponse response = service.searchSimilar(request).block();

        // Then: Should call repository with correct limit
        verify(repository).findNearest(any(float[].class), eq(3));
        assertTrue(response.docs().size() <= 3);
    }

    @Test
    void testSearchSimilar_withCustomMinScore_shouldFilterCorrectly() {
        // Given: Mock embedding
        mockEmbeddingModelResponse(createMockEmbedding());

        // Given: Mock repository results with varying scores
        List<ScoredDocument> mockResults = List.of(
            createScoredDocument("1", "Very relevant", 0.92),
            createScoredDocument("2", "Moderately relevant", 0.82),
            createScoredDocument("3", "Somewhat relevant", 0.72),
            createScoredDocument("4", "Less relevant", 0.62)
        );
        when(repository.findNearest(any(float[].class), anyInt())).thenReturn(mockResults);

        // When: Search with minScore=0.8
        KbSearchRequest request = new KbSearchRequest("test query", null, 0.8, null);
        KbSearchResponse response = service.searchSimilar(request).block();

        // Then: Should only return documents with score >= 0.8
        assertEquals(2, response.docs().size());
        assertTrue(response.hits().stream().allMatch(hit -> hit.score() >= 0.8));
    }

    @Test
    void testSearchSimilar_shouldBuildContextText() {
        // Given: Mock embedding
        mockEmbeddingModelResponse(createMockEmbedding());

        // Given: Documents with content
        List<ScoredDocument> mockResults = List.of(
            createScoredDocument("1", "First document content", 0.95),
            createScoredDocument("2", "Second document content", 0.85)
        );
        when(repository.findNearest(any(float[].class), anyInt())).thenReturn(mockResults);

        // When: Search
        KbSearchRequest request = new KbSearchRequest("test query", null, 0.7, null);
        KbSearchResponse response = service.searchSimilar(request).block();

        // Then: Context text should combine all document contents
        assertNotNull(response.contextText());
        assertTrue(response.contextText().contains("First document content"));
        assertTrue(response.contextText().contains("Second document content"));
        assertTrue(response.contextText().contains("\n\n")); // Documents separated by double newline
    }

    @Test
    void testSearchSimilar_emptyResults_shouldReturnEmptyResponse() {
        // Given: Mock embedding
        mockEmbeddingModelResponse(createMockEmbedding());

        // Given: No results from repository
        when(repository.findNearest(any(float[].class), anyInt())).thenReturn(List.of());

        // When: Search
        KbSearchRequest request = new KbSearchRequest("test query", null, null, null);
        KbSearchResponse response = service.searchSimilar(request).block();

        // Then: Should return empty but valid response
        assertNotNull(response);
        assertTrue(response.docs().isEmpty());
        assertTrue(response.hits().isEmpty());
        assertEquals("", response.contextText());
        assertNotNull(response.sourceTs());
    }

    @Test
    void testSearchSimilar_shouldCallEmbeddingModel() {
        // Given: Mock embedding
        mockEmbeddingModelResponse(createMockEmbedding());
        when(repository.findNearest(any(float[].class), anyInt())).thenReturn(List.of());

        // When: Search
        String queryText = "machine learning agents";
        KbSearchRequest request = new KbSearchRequest(queryText, null, null, null);
        service.searchSimilar(request).block();

        // Then: Should call embedding model with query text
        verify(embeddingModel).call(any(EmbeddingRequest.class));
    }

    @Test
    void testGetDocumentById_validId_shouldReturnDocument() {
        // Given: Mock repository to return a document
        KbDocument mockDoc = new KbDocument(
            "123",
            "article",
            "Test Document",
            "This is test content",
            Map.of("category", "test")
        );
        when(repository.findById(123L)).thenReturn(mockDoc);

        // When: Get document by ID
        KbDocument result = service.getDocumentById("123");

        // Then: Should return the document
        assertNotNull(result);
        assertEquals("123", result.id());
        assertEquals("Test Document", result.title());
        verify(repository).findById(123L);
    }

    @Test
    void testGetDocumentById_nonExistentId_shouldReturnNull() {
        // Given: Repository returns null
        when(repository.findById(anyLong())).thenReturn(null);

        // When: Get non-existent document
        KbDocument result = service.getDocumentById("999");

        // Then: Should return null
        assertNull(result);
    }

    @Test
    void testGetDocumentById_invalidIdFormat_shouldReturnNull() {
        // When: Get document with invalid ID format
        KbDocument result = service.getDocumentById("not-a-number");

        // Then: Should return null without calling repository
        assertNull(result);
        verify(repository, never()).findById(anyLong());
    }

    @Test
    void testSearchSimilar_withFilters_shouldPassThrough() {
        // Given: Mock embedding and results
        mockEmbeddingModelResponse(createMockEmbedding());
        when(repository.findNearest(any(float[].class), anyInt())).thenReturn(List.of());

        // When: Search with filters (currently not used in repository query, but should be preserved)
        Map<String, Object> filters = Map.of("category", "AI", "priority", 5);
        KbSearchRequest request = new KbSearchRequest("test query", null, null, filters);
        
        // Then: Should not throw exception
        assertDoesNotThrow(() -> service.searchSimilar(request).block());
    }

    /**
     * Helper method to create a mock embedding vector.
     */
    private float[] createMockEmbedding() {
        float[] embedding = new float[EMBEDDING_DIMENSIONS];
        for (int i = 0; i < EMBEDDING_DIMENSIONS; i++) {
            embedding[i] = 0.1f + (i * 0.0001f);
        }
        return embedding;
    }

    /**
     * Helper method to mock embedding model response.
     */
    private void mockEmbeddingModelResponse(float[] embedding) {
        Embedding mockEmbedding = mock(Embedding.class);
        when(mockEmbedding.getOutput()).thenReturn(embedding);

        EmbeddingResponse mockResponse = mock(EmbeddingResponse.class);
        when(mockResponse.getResults()).thenReturn(List.of(mockEmbedding));

        when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(mockResponse);
    }

    /**
     * Helper method to create a ScoredDocument for testing.
     */
    private ScoredDocument createScoredDocument(String id, String content, double score) {
        KbDocument doc = new KbDocument(
            id,
            "article",
            "Title " + id,
            content,
            Map.of("test", true)
        );
        return new ScoredDocument(doc, score);
    }
}
