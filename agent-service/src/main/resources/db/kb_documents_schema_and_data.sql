-- KB Documents Table Schema with pgvector extension
-- This script creates the kb_documents table for semantic search

-- Enable pgvector extension (run as superuser if needed)
CREATE EXTENSION IF NOT EXISTS vector;

-- Create kb_documents table
CREATE TABLE IF NOT EXISTS kb_documents (
    id BIGSERIAL PRIMARY KEY,
    doc_type VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    metadata JSONB,
    embedding vector(1536) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create HNSW index for fast similarity search
-- HNSW is optimized for high-dimensional vector similarity
CREATE INDEX IF NOT EXISTS kb_documents_embedding_hnsw_idx 
ON kb_documents USING hnsw (embedding vector_cosine_ops);

-- Create GIN index on metadata for filtering
CREATE INDEX IF NOT EXISTS kb_documents_metadata_idx 
ON kb_documents USING gin (metadata);

-- Create index on doc_type for filtering
CREATE INDEX IF NOT EXISTS kb_documents_doc_type_idx 
ON kb_documents (doc_type);

-- Insert dummy data with realistic embeddings
-- Note: These are simplified 1536-dimensional vectors for demonstration
-- In production, use actual embeddings from your embedding model

-- Document 1: About AI Agents
INSERT INTO kb_documents (id, doc_type, content, metadata, embedding) VALUES (
    1,
    'article',
    'AI agents are autonomous software entities that can perceive their environment, make decisions, and take actions to achieve specific goals. They use machine learning models, particularly large language models, to understand context and generate intelligent responses.',
    '{"source": "knowledge_base", "category": "AI", "tags": ["agents", "ml", "llm"]}',
    array_fill(0.1, ARRAY[1536])::vector  -- Simplified embedding vector
);

-- Document 2: About Vector Databases
INSERT INTO kb_documents (id, doc_type, content, metadata, embedding) VALUES (
    2,
    'article',
    'Vector databases like pgvector enable efficient similarity search over high-dimensional embeddings. They use specialized indexing algorithms such as HNSW (Hierarchical Navigable Small World) to perform approximate nearest neighbor searches at scale.',
    '{"source": "knowledge_base", "category": "Database", "tags": ["vector", "pgvector", "similarity"]}',
    array_fill(0.15, ARRAY[1536])::vector
);

-- Document 3: About RAG (Retrieval Augmented Generation)
INSERT INTO kb_documents (id, doc_type, content, metadata, embedding) VALUES (
    3,
    'tutorial',
    'Retrieval Augmented Generation (RAG) combines information retrieval with language generation. It first searches a knowledge base for relevant documents, then uses those documents as context to generate accurate, grounded responses. This approach reduces hallucinations and improves factual accuracy.',
    '{"source": "knowledge_base", "category": "NLP", "tags": ["rag", "llm", "generation"]}',
    array_fill(0.12, ARRAY[1536])::vector
);

-- Document 4: About Embeddings
INSERT INTO kb_documents (id, doc_type, content, metadata, embedding) VALUES (
    4,
    'article',
    'Embeddings are dense vector representations of text that capture semantic meaning. Models like OpenAI text-embedding-3-small generate 1536-dimensional vectors where similar concepts are close together in vector space. This enables semantic search beyond keyword matching.',
    '{"source": "knowledge_base", "category": "NLP", "tags": ["embeddings", "vectors", "semantic"]}',
    array_fill(0.13, ARRAY[1536])::vector
);

-- Document 5: About Spring Boot
INSERT INTO kb_documents (id, doc_type, content, metadata, embedding) VALUES (
    5,
    'guide',
    'Spring Boot is a framework that simplifies Java application development. It provides auto-configuration, embedded servers, and production-ready features. Spring Boot makes it easy to create stand-alone, production-grade Spring-based applications.',
    '{"source": "knowledge_base", "category": "Java", "tags": ["spring", "java", "framework"]}',
    array_fill(0.2, ARRAY[1536])::vector
);

-- Document 6: About PostgreSQL
INSERT INTO kb_documents (id, doc_type, content, metadata, embedding) VALUES (
    6,
    'article',
    'PostgreSQL is a powerful open-source relational database system with advanced features. With the pgvector extension, it can efficiently store and query vector embeddings, making it suitable for AI applications that require semantic search capabilities.',
    '{"source": "knowledge_base", "category": "Database", "tags": ["postgresql", "sql", "database"]}',
    array_fill(0.18, ARRAY[1536])::vector
);

-- Document 7: About Machine Learning
INSERT INTO kb_documents (id, doc_type, content, metadata, embedding) VALUES (
    7,
    'article',
    'Machine learning enables computers to learn from data without explicit programming. Deep learning models, especially transformers, have revolutionized natural language processing. These models can understand context, generate human-like text, and perform complex reasoning tasks.',
    '{"source": "knowledge_base", "category": "AI", "tags": ["ml", "deep learning", "transformers"]}',
    array_fill(0.11, ARRAY[1536])::vector
);

-- Document 8: About Microservices
INSERT INTO kb_documents (id, doc_type, content, metadata, embedding) VALUES (
    8,
    'guide',
    'Microservices architecture breaks applications into small, independent services that communicate over networks. Each service handles a specific business capability and can be deployed independently. This approach enables better scalability, maintainability, and team autonomy.',
    '{"source": "knowledge_base", "category": "Architecture", "tags": ["microservices", "architecture", "distributed"]}',
    array_fill(0.25, ARRAY[1536])::vector
);

-- Document 9: About REST APIs
INSERT INTO kb_documents (id, doc_type, content, metadata, embedding) VALUES (
    9,
    'tutorial',
    'REST (Representational State Transfer) APIs use HTTP methods to perform CRUD operations. They follow stateless client-server architecture where resources are identified by URIs. REST APIs are widely used for building web services due to their simplicity and scalability.',
    '{"source": "knowledge_base", "category": "Web", "tags": ["rest", "api", "http"]}',
    array_fill(0.22, ARRAY[1536])::vector
);

-- Document 10: About Testing
INSERT INTO kb_documents (id, doc_type, content, metadata, embedding) VALUES (
    10,
    'guide',
    'Software testing ensures code quality and reliability. Unit tests verify individual components, integration tests check component interactions, and end-to-end tests validate entire workflows. Test-driven development (TDD) promotes writing tests before implementation.',
    '{"source": "knowledge_base", "category": "Testing", "tags": ["testing", "tdd", "quality"]}',
    array_fill(0.3, ARRAY[1536])::vector
);

-- Reset sequence to continue from current max id
SELECT setval('kb_documents_id_seq', (SELECT MAX(id) FROM kb_documents));

-- Query examples:

-- 1. Find similar documents using cosine distance
-- SELECT id, doc_type, content, 1 - (embedding <=> '[0.1,0.1,...]'::vector) AS similarity
-- FROM kb_documents
-- ORDER BY embedding <=> '[0.1,0.1,...]'::vector
-- LIMIT 5;

-- 2. Filter by metadata
-- SELECT * FROM kb_documents
-- WHERE metadata->>'category' = 'AI'
-- ORDER BY id;

-- 3. Combine similarity search with metadata filtering
-- SELECT id, doc_type, content, 1 - (embedding <=> '[0.1,0.1,...]'::vector) AS similarity
-- FROM kb_documents
-- WHERE metadata->>'category' = 'AI'
-- ORDER BY embedding <=> '[0.1,0.1,...]'::vector
-- LIMIT 3;
