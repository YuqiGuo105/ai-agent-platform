package com.mrpot.agent.repository;

import com.mrpot.agent.common.kb.KbDocument;
import com.mrpot.agent.common.kb.ScoredDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Repository for querying KB documents using pgvector similarity search.
 * Uses PostgreSQL's pgvector extension with cosine distance operator.
 */
@Repository
@RequiredArgsConstructor
public class KbDocumentVectorRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Find nearest documents using pgvector's cosine distance operator `<=>`.
     * Calculates similarity score as 1 - distance.
     *
     * @param embedding the query embedding vector
     * @param limit maximum number of results to return
     * @return list of documents with similarity scores
     */
    public List<ScoredDocument> findNearest(float[] embedding, int limit) {
        PGvector queryVector = new PGvector(embedding);

        String sql = """
                SELECT id,
                       doc_type,
                       content,
                       metadata,
                       1 - (embedding <=> ?) AS score
                FROM kb_documents
                ORDER BY embedding <=> ?
                LIMIT ?
                """;

        return jdbcTemplate.query(sql, ps -> {
            ps.setObject(1, queryVector); // for 1 - (embedding <=> ?)
            ps.setObject(2, queryVector); // for ORDER BY embedding <=> ?
            ps.setInt(3, limit);
        }, new ScoredDocumentRowMapper());
    }

    /**
     * Find a document by its ID.
     *
     * @param id the document ID
     * @return the document if found, null otherwise
     */
    public KbDocument findById(Long id) {
        String sql = """
                SELECT id,
                       doc_type,
                       content,
                       metadata
                FROM kb_documents
                WHERE id = ?
                """;

        List<KbDocument> results = jdbcTemplate.query(sql, ps -> ps.setLong(1, id), new KbDocumentRowMapper());
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Row mapper for ScoredDocument that includes similarity score.
     */
    private class ScoredDocumentRowMapper implements RowMapper<ScoredDocument> {
        @Override
        public ScoredDocument mapRow(ResultSet rs, int rowNum) throws SQLException {
            // Convert Long id to String for KbDocument record
            String id = String.valueOf(rs.getLong("id"));
            String docType = rs.getString("doc_type");
            String content = rs.getString("content");

            JsonNode metadataNode = null;
            String metadataJson = rs.getString("metadata");
            if (metadataJson != null) {
                try {
                    metadataNode = objectMapper.readTree(metadataJson);
                } catch (Exception e) {
                    // If parsing fails, set to null
                    metadataNode = null;
                }
            }

            // Convert JsonNode to Map for KbDocument
            Map<String, Object> metadata = null;
            if (metadataNode != null && metadataNode.isObject()) {
                metadata = objectMapper.convertValue(metadataNode, Map.class);
            }

            KbDocument doc = new KbDocument(id, docType, null, content, metadata);
            double score = rs.getDouble("score");
            return new ScoredDocument(doc, score);
        }
    }

    /**
     * Row mapper for KbDocument without score.
     */
    private class KbDocumentRowMapper implements RowMapper<KbDocument> {
        @Override
        public KbDocument mapRow(ResultSet rs, int rowNum) throws SQLException {
            String id = String.valueOf(rs.getLong("id"));
            String docType = rs.getString("doc_type");
            String content = rs.getString("content");

            JsonNode metadataNode = null;
            String metadataJson = rs.getString("metadata");
            if (metadataJson != null) {
                try {
                    metadataNode = objectMapper.readTree(metadataJson);
                } catch (Exception e) {
                    metadataNode = null;
                }
            }

            Map<String, Object> metadata = null;
            if (metadataNode != null && metadataNode.isObject()) {
                metadata = objectMapper.convertValue(metadataNode, Map.class);
            }

            return new KbDocument(id, docType, null, content, metadata);
        }
    }
}
