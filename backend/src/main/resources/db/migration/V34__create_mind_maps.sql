-- V21: 思维导图持久化
CREATE TABLE mind_maps (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id),
    note_id TEXT REFERENCES notes(id) ON DELETE SET NULL,
    title TEXT NOT NULL,
    data JSONB NOT NULL,
    layout_config JSONB DEFAULT '{}',
    source TEXT DEFAULT 'manual',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_mind_maps_user_id ON mind_maps(user_id);
CREATE INDEX idx_mind_maps_note_id ON mind_maps(note_id);
