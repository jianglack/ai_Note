-- 添加新字段到 embeddings 表
ALTER TABLE embeddings ADD COLUMN content_hash VARCHAR(32);
ALTER TABLE embeddings ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE embeddings ADD COLUMN provider VARCHAR(50);
ALTER TABLE embeddings ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE embeddings ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE embeddings ADD COLUMN error_message TEXT;

-- 创建索引
CREATE INDEX idx_embeddings_status ON embeddings(status);
CREATE INDEX idx_embeddings_content_hash ON embeddings(content_hash);

-- 更新现有记录的状态
UPDATE embeddings SET status = 'DONE' WHERE vector IS NOT NULL;
UPDATE embeddings SET status = 'PENDING' WHERE vector IS NULL;
