-- V45: 记忆系统工程化升级
-- 1. semantic_memories 加 embedding 向量列（语义去重）
-- 2. semantic_memories 加 domain/weight 列（已在实体中定义但表中缺失则跳过）
-- 3. user_memories 加 sequence_number 列（批量写入优化）

-- ============================================
-- 1. 语义记忆：添加 embedding 向量列用于模糊去重
-- ============================================
ALTER TABLE semantic_memories
    ADD COLUMN IF NOT EXISTS embedding vector(1024);

-- 向量相似度索引（IVFFlat，用于 cosine 距离检索）
CREATE INDEX IF NOT EXISTS idx_semantic_embedding
    ON semantic_memories USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 10);

-- 用于时间衰减排序的索引
CREATE INDEX IF NOT EXISTS idx_semantic_decay_sort
    ON semantic_memories(user_id, last_reinforced_at DESC NULLS LAST);

-- ============================================
-- 2. 对话记忆：添加 sequence_number 列
-- ============================================
ALTER TABLE user_memories
    ADD COLUMN IF NOT EXISTS sequence_number INTEGER;

-- 按 user_id + sequence_number 排序的索引
CREATE INDEX IF NOT EXISTS idx_user_memories_seq
    ON user_memories(user_id, sequence_number);

-- 为已有数据填充 sequence_number（按 created_at 排序）
UPDATE user_memories um
SET sequence_number = sub.rn
FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY created_at ASC) AS rn
    FROM user_memories
) sub
WHERE um.id = sub.id AND um.sequence_number IS NULL;

-- ============================================
-- 3. 语义记忆容量管理字段
-- ============================================
-- 衰减分数（由应用层计算并定期更新，方便排序）
ALTER TABLE semantic_memories
    ADD COLUMN IF NOT EXISTS decay_score DOUBLE PRECISION DEFAULT 0.8;

-- 衰减分数索引（用于 top-N 查询和淘汰）
CREATE INDEX IF NOT EXISTS idx_semantic_decay_score
    ON semantic_memories(user_id, decay_score DESC);

COMMENT ON COLUMN semantic_memories.embedding IS '记忆内容的 embedding 向量（1024维），用于语义去重';
COMMENT ON COLUMN semantic_memories.decay_score IS '综合衰减分数 = confidence × timesReinforced × timeDecay，定期更新';
COMMENT ON COLUMN user_memories.sequence_number IS '消息在对话中的序号，用于增量更新';
