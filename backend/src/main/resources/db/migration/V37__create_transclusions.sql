-- V24: Transclusions (content embedding between notes)

CREATE TABLE transclusions (
    id TEXT PRIMARY KEY,
    source_note_id TEXT NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    target_note_id TEXT NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    source_block_id TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_transclusions_source ON transclusions(source_note_id);
CREATE INDEX idx_transclusions_target ON transclusions(target_note_id);
