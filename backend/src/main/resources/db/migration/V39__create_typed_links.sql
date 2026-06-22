-- V26: Typed links (semantic relationships between notes)

CREATE TABLE typed_links (
    id TEXT PRIMARY KEY,
    source_note_id TEXT NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    target_note_id TEXT NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    relation_type TEXT NOT NULL,
    context TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_typed_links_source ON typed_links(source_note_id);
CREATE INDEX idx_typed_links_target ON typed_links(target_note_id);
CREATE INDEX idx_typed_links_type ON typed_links(relation_type);
