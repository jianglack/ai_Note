-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 用户表
CREATE TABLE users (
    id TEXT PRIMARY KEY,
    username TEXT UNIQUE NOT NULL,
    email TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- 文件夹表
CREATE TABLE folders (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    parent_id TEXT REFERENCES folders(id),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- 笔记表
CREATE TABLE notes (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    user_id TEXT NOT NULL REFERENCES users(id),
    folder_id TEXT REFERENCES folders(id),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP
);

CREATE INDEX idx_notes_user_id ON notes(user_id);
CREATE INDEX idx_notes_deleted_at ON notes(deleted_at);
CREATE INDEX idx_notes_folder_id ON notes(folder_id);

-- 标签表
CREATE TABLE tags (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

-- 笔记标签关联表
CREATE TABLE note_tags (
    note_id TEXT NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    tag_id TEXT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (note_id, tag_id)
);

-- 笔记版本表
CREATE TABLE note_versions (
    id TEXT PRIMARY KEY,
    note_id TEXT NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_note_versions_note_id ON note_versions(note_id);

-- 向量嵌入表
CREATE TABLE embeddings (
    note_id TEXT PRIMARY KEY REFERENCES notes(id) ON DELETE CASCADE,
    vector vector(1536) NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- HNSW 索引，余弦相似度
CREATE INDEX idx_embeddings_vector ON embeddings USING hnsw (vector vector_cosine_ops);

-- 每日总结表
CREATE TABLE daily_summary (
    date TEXT PRIMARY KEY,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);
