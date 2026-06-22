-- 用户对话记忆表（短期记忆，用于 ChatMemory）
CREATE TABLE user_memories (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    message_type VARCHAR(20) NOT NULL, -- USER, AI, SYSTEM, TOOL_EXECUTION_RESULT
    content TEXT NOT NULL,
    tool_name VARCHAR(64),
    tool_result TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX idx_user_memories_user_id ON user_memories(user_id);
CREATE INDEX idx_user_memories_created_at ON user_memories(created_at);
CREATE INDEX idx_user_memories_user_created ON user_memories(user_id, created_at DESC);

-- 用户偏好表（长期记忆）
CREATE TABLE user_preferences (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL UNIQUE,
    preferences JSONB DEFAULT '{}',
    learned_facts JSONB DEFAULT '[]',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX idx_user_preferences_user_id ON user_preferences(user_id);
