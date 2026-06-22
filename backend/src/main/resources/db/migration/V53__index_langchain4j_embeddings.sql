-- LangChain4j PgVectorEmbeddingStore creates this table at runtime.
-- Keep Flyway replayable by creating the matching default schema when absent.
CREATE TABLE IF NOT EXISTS langchain4j_embeddings (
    embedding_id UUID PRIMARY KEY,
    embedding vector(1024),
    text TEXT NULL,
    metadata JSON NULL
);

CREATE INDEX IF NOT EXISTS idx_l4j_embeddings_vec
    ON langchain4j_embeddings USING hnsw (embedding vector_cosine_ops);

-- LangChain4j 1.12 default metadata column is JSON, so cast for GIN.
CREATE INDEX IF NOT EXISTS idx_l4j_embeddings_meta
    ON langchain4j_embeddings USING gin ((metadata::jsonb));

-- Actual generated filters and cleanup use metadata->>'userId' / noteId.
CREATE INDEX IF NOT EXISTS idx_l4j_embeddings_meta_user_id
    ON langchain4j_embeddings ((metadata->>'userId'));

CREATE INDEX IF NOT EXISTS idx_l4j_embeddings_meta_note_id
    ON langchain4j_embeddings ((metadata->>'noteId'));
