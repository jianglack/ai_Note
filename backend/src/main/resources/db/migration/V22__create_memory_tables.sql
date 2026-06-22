-- V22: Create long-term memory tables (semantic + episodic)

-- 语义记忆表：存储从对话中提炼的用户偏好、事实、习惯
CREATE TABLE semantic_memories (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    category VARCHAR(30) NOT NULL,        -- preference / fact / habit / style
    content TEXT NOT NULL,                 -- 提炼的内容
    confidence DOUBLE PRECISION DEFAULT 0.8,
    source VARCHAR(20) NOT NULL DEFAULT 'ai_extracted',  -- ai_extracted / user_explicit
    times_reinforced INTEGER DEFAULT 1,
    last_reinforced_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_semantic_user ON semantic_memories(user_id);
CREATE INDEX idx_semantic_category ON semantic_memories(user_id, category);

COMMENT ON TABLE semantic_memories IS '语义记忆 - 用户偏好、事实、习惯的长期存储';
COMMENT ON COLUMN semantic_memories.category IS '分类: preference(偏好), fact(事实), habit(习惯), style(交互风格)';
COMMENT ON COLUMN semantic_memories.confidence IS '置信度 0-1, 越高越可信';
COMMENT ON COLUMN semantic_memories.times_reinforced IS '被多次对话印证的次数, 越高越稳定';

-- 情节记忆表：存储每次会话结束时的整体摘要
CREATE TABLE episodic_memories (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    session_summary TEXT NOT NULL,          -- 本次会话摘要
    key_topics TEXT,                        -- 主要话题（JSON 数组）
    actions_taken TEXT,                     -- 执行的操作（JSON 数组）
    message_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_episodic_user ON episodic_memories(user_id);
CREATE INDEX idx_episodic_time ON episodic_memories(user_id, created_at DESC);

COMMENT ON TABLE episodic_memories IS '情节记忆 - 每次会话的摘要记录';
COMMENT ON COLUMN episodic_memories.session_summary IS '会话摘要（100字以内）';
COMMENT ON COLUMN episodic_memories.key_topics IS '主要话题 JSON 数组';
COMMENT ON COLUMN episodic_memories.actions_taken IS '执行的操作 JSON 数组';
