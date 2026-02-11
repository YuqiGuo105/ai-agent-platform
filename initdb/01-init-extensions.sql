-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Enable other useful extensions
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS btree_gin;

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE aiagent TO aiagent;
