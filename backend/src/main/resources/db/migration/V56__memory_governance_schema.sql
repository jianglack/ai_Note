-- Phase 1: memory governance schema.
-- Additive migration only: keep existing memory tables and legacy paths readable.

ALTER TABLE semantic_memories
    ALTER COLUMN user_id TYPE VARCHAR(128),
    ALTER COLUMN source TYPE VARCHAR(50),
    ADD COLUMN IF NOT EXISTS memory_type VARCHAR(30) DEFAULT 'semantic',
    ADD COLUMN IF NOT EXISTS scope VARCHAR(50) DEFAULT 'user',
    ADD COLUMN IF NOT EXISTS status VARCHAR(30) DEFAULT 'active',
    ADD COLUMN IF NOT EXISTS source_trace_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS source_message_ids TEXT,
    ADD COLUMN IF NOT EXISTS source_tool_call_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS evidence_excerpt TEXT,
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_accessed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS access_count INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS supersedes_id BIGINT,
    ADD COLUMN IF NOT EXISTS content_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS metadata_json TEXT;

UPDATE semantic_memories SET memory_type = COALESCE(memory_type, category, 'semantic');
UPDATE semantic_memories SET scope = COALESCE(scope, 'user');
UPDATE semantic_memories SET status = COALESCE(status, 'active');
UPDATE semantic_memories SET access_count = COALESCE(access_count, 0);

CREATE INDEX IF NOT EXISTS idx_semantic_user_status_type
    ON semantic_memories(user_id, status, memory_type);
CREATE INDEX IF NOT EXISTS idx_semantic_user_scope_status
    ON semantic_memories(user_id, scope, status);
CREATE INDEX IF NOT EXISTS idx_semantic_user_decay_score
    ON semantic_memories(user_id, decay_score DESC);
CREATE INDEX IF NOT EXISTS idx_semantic_content_hash
    ON semantic_memories(content_hash);

ALTER TABLE episodic_memories
    ALTER COLUMN user_id TYPE VARCHAR(128),
    ADD COLUMN IF NOT EXISTS session_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS thread_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS started_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS ended_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS summary_embedding vector(1024),
    ADD COLUMN IF NOT EXISTS status VARCHAR(30) DEFAULT 'active',
    ADD COLUMN IF NOT EXISTS metadata_json TEXT,
    ADD COLUMN IF NOT EXISTS source_message_range TEXT;

UPDATE episodic_memories SET status = COALESCE(status, 'active');

CREATE INDEX IF NOT EXISTS idx_episodic_user_status_time
    ON episodic_memories(user_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_episodic_summary_embedding
    ON episodic_memories USING ivfflat (summary_embedding vector_cosine_ops)
    WITH (lists = 10);

CREATE TABLE IF NOT EXISTS memory_events (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    memory_id BIGINT,
    event_type VARCHAR(40) NOT NULL,
    actor VARCHAR(30) NOT NULL,
    reason TEXT,
    before_json TEXT,
    after_json TEXT,
    trace_id VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_memory_events_user_time
    ON memory_events(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_memory_events_memory_id
    ON memory_events(memory_id);
CREATE INDEX IF NOT EXISTS idx_memory_events_type
    ON memory_events(event_type);
