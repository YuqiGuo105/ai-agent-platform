package com.mrpot.agent.knowledge.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrpot.agent.common.kb.KbDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@Slf4j
@Repository
@RequiredArgsConstructor
public class KbDocumentRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    // ─── Find All (paginated) ────────────────────────────────────────
    public List<KbDocument> findAll(int page, int size) {
        int offset = page * size;
        String sql = """
                SELECT id, doc_type, content, metadata
                FROM kb_documents
                ORDER BY id DESC
                LIMIT ? OFFSET ?
                """;
        return jdbcTemplate.query(sql, ps -> {
            ps.setInt(1, size);
            ps.setInt(2, offset);
        }, new KbDocumentRowMapper());
    }

    // ─── Find By ID ─────────────────────────────────────────────────
    public KbDocument findById(Long id) {
        String sql = """
                SELECT id, doc_type, content, metadata
                FROM kb_documents
                WHERE id = ?
                """;
        List<KbDocument> results = jdbcTemplate.query(sql,
                ps -> ps.setLong(1, id), new KbDocumentRowMapper());
        return results.isEmpty() ? null : results.get(0);
    }

    // ─── Delete By ID ───────────────────────────────────────────────
    public boolean deleteById(Long id) {
        String sql = "DELETE FROM kb_documents WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, id);
        return rowsAffected > 0;
    }

    // ─── Fuzzy Search ───────────────────────────────────────────────
    public List<KbDocument> fuzzySearch(String keyword, String docType, int page, int size) {
        int offset = page * size;
        String pattern = "%" + keyword + "%";

        // If docType is provided, add it as a filter
        if (docType != null && !docType.isBlank()) {
            String sql = """
                    SELECT id, doc_type, content, metadata
                    FROM kb_documents
                    WHERE (content ILIKE ? OR doc_type ILIKE ? OR metadata::text ILIKE ?)
                      AND doc_type = ?
                    ORDER BY id DESC
                    LIMIT ? OFFSET ?
                    """;
            return jdbcTemplate.query(sql, ps -> {
                ps.setString(1, pattern);
                ps.setString(2, pattern);
                ps.setString(3, pattern);
                ps.setString(4, docType);
                ps.setInt(5, size);
                ps.setInt(6, offset);
            }, new KbDocumentRowMapper());
        } else {
            String sql = """
                    SELECT id, doc_type, content, metadata
                    FROM kb_documents
                    WHERE (content ILIKE ? OR doc_type ILIKE ? OR metadata::text ILIKE ?)
                    ORDER BY id DESC
                    LIMIT ? OFFSET ?
                    """;
            return jdbcTemplate.query(sql, ps -> {
                ps.setString(1, pattern);
                ps.setString(2, pattern);
                ps.setString(3, pattern);
                ps.setInt(4, size);
                ps.setInt(5, offset);
            }, new KbDocumentRowMapper());
        }
    }

    // ─── Count documents ────────────────────────────────────────────
    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM kb_documents", Long.class);
        return count != null ? count : 0;
    }

    // ─── Row Mapper ─────────────────────────────────────────────────
    private class KbDocumentRowMapper implements RowMapper<KbDocument> {
        @Override
        public KbDocument mapRow(ResultSet rs, int rowNum) throws SQLException {
            String id = String.valueOf(rs.getLong("id"));
            String docType = rs.getString("doc_type");
            String content = rs.getString("content");

            // Parse JSONB metadata
            String metadataJson = rs.getString("metadata");
            Map<String, Object> metadata = null;
            if (metadataJson != null) {
                try {
                    JsonNode metadataNode = objectMapper.readTree(metadataJson);
                    if (metadataNode.isObject()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> parsed = objectMapper.convertValue(metadataNode, Map.class);
                        metadata = parsed;
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse metadata JSON for document {}: {}", id, e.getMessage());
                }
            }

            return new KbDocument(id, docType, null, content, metadata);
        }
    }
}
