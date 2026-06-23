-- Supports freshness checks before regenerating embeddings for unchanged notes.
CREATE INDEX IF NOT EXISTS idx_l4j_embeddings_note_content_hash
    ON langchain4j_embeddings ((metadata->>'noteId'), (metadata->>'contentHash'));
