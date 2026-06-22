-- RAG feedback for adaptive threshold tuning
CREATE TABLE rag_feedback (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    query TEXT NOT NULL,
    result_note_id VARCHAR(255),
    similarity_score DOUBLE PRECISION NOT NULL,
    feedback_type VARCHAR(20) NOT NULL,  -- THUMBS_UP, THUMBS_DOWN, CLICK
    weight DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rag_feedback_user ON rag_feedback(user_id);
CREATE INDEX idx_rag_feedback_created ON rag_feedback(created_at);
