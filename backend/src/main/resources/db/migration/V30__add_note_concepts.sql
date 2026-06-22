-- V30: Note concepts table for content understanding
CREATE TABLE IF NOT EXISTS note_concepts (
    id BIGSERIAL PRIMARY KEY,
    note_id VARCHAR(36) NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    user_id VARCHAR(36) NOT NULL REFERENCES users(id),
    concept VARCHAR(100) NOT NULL,
    category VARCHAR(30) NOT NULL DEFAULT 'keyword',
    confidence DOUBLE PRECISION NOT NULL DEFAULT 0.8,
    extracted_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(note_id, concept)
);

CREATE INDEX idx_note_concepts_user ON note_concepts(user_id);
CREATE INDEX idx_note_concepts_concept ON note_concepts(concept);
CREATE INDEX idx_note_concepts_note ON note_concepts(note_id);
