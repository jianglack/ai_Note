-- V20: 多模态内容支持 - 图片和表格存储
CREATE TABLE note_media (
    id TEXT PRIMARY KEY,
    note_id TEXT NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES users(id),
    media_type TEXT NOT NULL,
    filename TEXT,
    mime_type TEXT,
    data_base64 TEXT NOT NULL,
    ocr_text TEXT,
    table_json JSONB,
    table_markdown TEXT,
    metadata JSONB DEFAULT '{}',
    file_size_bytes INTEGER,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_note_media_note_id ON note_media(note_id);
CREATE INDEX idx_note_media_user_id ON note_media(user_id);
CREATE INDEX idx_note_media_type ON note_media(media_type);
