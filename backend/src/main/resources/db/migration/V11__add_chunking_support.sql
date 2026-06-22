-- RAG 分块支持：将 embeddings 表从一对一改为一对多

-- 1. 添加新列
ALTER TABLE embeddings ADD COLUMN IF NOT EXISTS id VARCHAR(36);
ALTER TABLE embeddings ADD COLUMN IF NOT EXISTS chunk_index INTEGER DEFAULT 0;
ALTER TABLE embeddings ADD COLUMN IF NOT EXISTS chunk_text TEXT;

-- 2. 为现有记录生成 ID
UPDATE embeddings SET id = gen_random_uuid()::text WHERE id IS NULL;

-- 3. 删除旧主键，添加新主键
ALTER TABLE embeddings DROP CONSTRAINT IF EXISTS embeddings_pkey;
ALTER TABLE embeddings ALTER COLUMN id SET NOT NULL;
ALTER TABLE embeddings ADD PRIMARY KEY (id);

-- 4. 添加索引
CREATE INDEX IF NOT EXISTS idx_embeddings_note_chunk ON embeddings(note_id, chunk_index);
CREATE INDEX IF NOT EXISTS idx_embeddings_note_id ON embeddings(note_id);
