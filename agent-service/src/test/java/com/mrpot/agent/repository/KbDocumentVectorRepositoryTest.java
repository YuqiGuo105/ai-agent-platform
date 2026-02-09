package com.mrpot.agent.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrpot.agent.common.kb.KbDocument;
import com.mrpot.agent.common.kb.ScoredDocument;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for KbDocumentVectorRepository.
 * Tests pgvector similarity search functionality using SQL dummy data.
 * 
 * Note: This test requires a PostgreSQL database with pgvector extension.
 * The test data is loaded from db/kb_documents_schema_and_data.sql
 * 
 * To run this test:
 * 1. Start PostgreSQL with pgvector extension: docker run -d -p 5432:5432 -e POSTGRES_PASSWORD=postgres pgvector/pgvector:pg16
 * 2. Create test database: psql -U postgres -c "CREATE DATABASE testdb;"
 * 3. Remove @Disabled annotation
 * 4. Run: mvn test -Dtest=KbDocumentVectorRepositoryTest
 */
@Disabled("Requires PostgreSQL with pgvector extension. See class javadoc for setup instructions.")
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
@Sql(scripts = "/db/kb_documents_schema_and_data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "classpath:cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class KbDocumentVectorRepositoryTest {

    @Autowired
    private KbDocumentVectorRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final int EMBEDDING_DIMENSIONS = 1536;

    @Test
    void testFindNearest_shouldReturnDocumentsOrderedBySimilarity() {
        // Given: SQL dummy data with known embeddings
        // Doc 1: 0.1 (AI Agents), Doc 7: 0.11 (ML), Doc 3: 0.12 (RAG)
        
        // When: Search for documents similar to AI embedding (0.1)
        float[] queryEmbedding = createEmbedding(0.1f);
        List<ScoredDocument> results = repository.findNearest(queryEmbedding, 3);

        // Then: Should return documents ordered by similarity
        assertNotNull(results);
        assertEquals(3, results.size());

        // First result should be Doc 1 (embedding 0.1 - AI Agents)
        ScoredDocument first = results.get(0);
        assertEquals("1", first.document().id());
        assertTrue(first.document().content().contains("AI agents"), "Should be about AI agents");
        assertTrue(first.score() > 0.9, "First document should have high similarity score, got: " + first.score());

        // Second result should be Doc 7 (embedding 0.11 - Machine Learning)
        ScoredDocument second = results.get(1);
        assertEquals("7", second.document().id());
        assertTrue(second.document().content().contains("Machine learning"), "Should be about ML");

        // Third result should be Doc 3 (embedding 0.12 - RAG)
        ScoredDocument third = results.get(2);
        assertEquals("3", third.document().id());
        assertTrue(third.document().content().contains("RAG"), "Should be about RAG");
        
        // Verify scores are ordered
        assertTrue(first.score() >= second.score(), "Scores should be ordered by similarity");
        assertTrue(second.score() >= third.score(), "Scores should be ordered by similarity");
    }

    @Test
    void testFindNearest_withLimit_shouldReturnLimitedResults() {
        // Given: SQL dummy data has 10 documents
        
        // When: Search with limit of 3
        float[] queryEmbedding = createEmbedding(0.15f);
        List<ScoredDocument> results = repository.findNearest(queryEmbedding, 3);

        // Then: Should return only 3 results even though there are 10 documents
        assertEquals(3, results.size());
        
        // When: Search with limit of 5
        results = repository.findNearest(queryEmbedding, 5);
        
        // Then: Should return 5 results
        assertEquals(5, results.size());
    }

    @Test
    void testFindNearest_shouldCalculateCorrectSimilarityScore() {
        // Given: SQL data has Doc 1 with embedding 0.1
        
        // When: Search with identical embedding (0.1)
        float[] embedding = createEmbedding(0.1f);
        List<ScoredDocument> results = repository.findNearest(embedding, 1);

        // Then: Score should be very close to 1.0 (identical vectors)
        assertFalse(results.isEmpty());
        ScoredDocument result = results.get(0);
        assertEquals("1", result.document().id(), "Should return Doc 1 (AI Agents)");
        assertTrue(result.score() > 0.99, "Identical vectors should have score close to 1.0, got: " + result.score());
    }

    @Test
    void testFindNearest_shouldIncludeMetadata() {
        // Given: SQL data has Doc 1 with metadata containing category "AI"
        
        // When: Find documents similar to embedding 0.1
        List<ScoredDocument> results = repository.findNearest(createEmbedding(0.1f), 1);

        // Then: Should include metadata from SQL data
        assertFalse(results.isEmpty());
        KbDocument doc = results.get(0).document();
        assertNotNull(doc.metadata(), "Metadata should not be null");
        assertEquals("AI", doc.metadata().get("category"), "Category should be AI");
        assertEquals("knowledge_base", doc.metadata().get("source"), "Source should be knowledge_base");
    }

    @Test
    void testFindById_shouldReturnDocument() {
        // Given: SQL data has Doc 5 (Spring Boot guide)
        
        // When: Find by ID 5
        KbDocument result = repository.findById(5L);

        // Then: Should return the document
        assertNotNull(result);
        assertEquals("5", result.id());
        assertTrue(result.content().contains("Spring Boot"), "Content should be about Spring Boot");
        assertEquals("guide", result.docType());
    }

    @Test
    void testFindById_nonExistentId_shouldReturnNull() {
        // When: Find non-existent document
        KbDocument result = repository.findById(999999L);

        // Then: Should return null
        assertNull(result);
    }

    @Test
    void testFindNearest_withDifferentEmbeddings_shouldRankCorrectly() {
        // Given: SQL data with various embeddings
        // Doc 1: 0.1, Doc 2: 0.15, Doc 5: 0.2, Doc 10: 0.3
        
        // When: Search for documents similar to 0.2 (Spring Boot)
        List<ScoredDocument> results = repository.findNearest(createEmbedding(0.2f), 10);

        // Then: Should return all 10 documents, ranked by similarity
        assertEquals(10, results.size());
        
        // Doc 5 (0.2) should be first
        assertEquals("5", results.get(0).document().id());
        
        // Scores should be in descending order
        for (int i = 0; i < results.size() - 1; i++) {
            assertTrue(results.get(i).score() >= results.get(i + 1).score(),
                    "Scores should be ordered from highest to lowest");
        }
    }

    @Test
    void testFindById_withDifferentDocTypes_shouldReturnCorrectType() {
        // Given: SQL data has different doc types
        // Doc 1: article, Doc 3: tutorial, Doc 5: guide
        
        // When: Find documents with different types
        KbDocument article = repository.findById(1L);
        KbDocument tutorial = repository.findById(3L);
        KbDocument guide = repository.findById(5L);

        // Then: Should return correct doc types
        assertNotNull(article);
        assertEquals("article", article.docType());
        
        assertNotNull(tutorial);
        assertEquals("tutorial", tutorial.docType());
        
        assertNotNull(guide);
        assertEquals("guide", guide.docType());
    }

    /**
     * Helper method to create a simple embedding vector matching SQL data format.
     * SQL data uses array_fill(value, ARRAY[1536]) which creates uniform vectors.
     */
    private float[] createEmbedding(float fillValue) {
        float[] embedding = new float[EMBEDDING_DIMENSIONS];
        for (int i = 0; i < EMBEDDING_DIMENSIONS; i++) {
            // Create uniform vector matching SQL data format
            embedding[i] = fillValue;
        }
        return embedding;
    }
}
