-- V23: Note databases (Notion-like inline databases)

CREATE TABLE note_databases (
    id TEXT PRIMARY KEY,
    note_id TEXT NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES users(id),
    name TEXT NOT NULL,
    columns JSONB NOT NULL,
    view_config JSONB DEFAULT '{}',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE note_database_rows (
    id TEXT PRIMARY KEY,
    database_id TEXT NOT NULL REFERENCES note_databases(id) ON DELETE CASCADE,
    data JSONB NOT NULL,
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_note_databases_note_id ON note_databases(note_id);
CREATE INDEX idx_note_db_rows_database_id ON note_database_rows(database_id);
CREATE INDEX idx_note_db_rows_data ON note_database_rows USING gin(data);
