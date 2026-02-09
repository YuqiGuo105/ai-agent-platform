package com.mrpot.agent.common.kb;

import com.fasterxml.jackson.databind.JsonNode;
import com.pgvector.PGvector;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity class representing the kb_documents database table.
 * Maps to a PostgreSQL table with pgvector extension for semantic search.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KbDocumentEntity {
    private Long id;
    private String docType;
    private String content;
    private JsonNode metadata;
    private PGvector embedding;
}
