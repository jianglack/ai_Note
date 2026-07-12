-- Task 8: bounded, append-only, concurrency-safe short-term chat memory.

ALTER TABLE user_memories
    ADD COLUMN IF NOT EXISTS trimmed_at TIMESTAMP;

-- Normalize every user's existing order before enforcing the invariant. Existing
-- non-null sequence values remain the primary order; null values are appended.
WITH normalized AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY user_id
               ORDER BY CASE WHEN sequence_number IS NULL THEN 1 ELSE 0 END,
                        sequence_number NULLS LAST,
                        created_at,
                        id
           ) - 1 AS normalized_sequence
    FROM user_memories
)
UPDATE user_memories target
SET sequence_number = normalized.normalized_sequence
FROM normalized
WHERE target.id = normalized.id
  AND target.sequence_number IS DISTINCT FROM normalized.normalized_sequence;

ALTER TABLE user_memories
    ALTER COLUMN sequence_number SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_memories_user_sequence
    ON user_memories(user_id, sequence_number);

CREATE INDEX IF NOT EXISTS idx_user_memories_model_window
    ON user_memories(user_id, trimmed_at, sequence_number DESC);

CREATE TABLE IF NOT EXISTS chat_memory_heads (
    user_id VARCHAR(128) PRIMARY KEY,
    next_sequence_number INTEGER NOT NULL,
    last_compaction_enqueued_sequence INTEGER NOT NULL DEFAULT -1,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO chat_memory_heads(user_id, next_sequence_number, last_compaction_enqueued_sequence)
SELECT user_id, COALESCE(MAX(sequence_number), -1) + 1, -1
FROM user_memories
GROUP BY user_id
ON CONFLICT (user_id) DO UPDATE
SET next_sequence_number = GREATEST(chat_memory_heads.next_sequence_number, EXCLUDED.next_sequence_number),
    updated_at = NOW();

CREATE TABLE IF NOT EXISTS chat_memory_compaction_jobs (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL REFERENCES chat_memory_heads(user_id) ON DELETE CASCADE,
    from_sequence INTEGER NOT NULL,
    to_sequence INTEGER NOT NULL,
    user_message_count INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'pending',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP,
    lease_until TIMESTAMP,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP,
    CONSTRAINT ck_chat_memory_compaction_range CHECK (from_sequence <= to_sequence),
    CONSTRAINT uq_chat_memory_compaction_range UNIQUE (user_id, from_sequence, to_sequence)
);

CREATE INDEX IF NOT EXISTS idx_chat_memory_compaction_claim
    ON chat_memory_compaction_jobs(status, next_attempt_at, lease_until, created_at);

CREATE INDEX IF NOT EXISTS idx_chat_memory_compaction_user_range
    ON chat_memory_compaction_jobs(user_id, from_sequence, to_sequence);

-- Replaying a job after a worker crash must not duplicate the same summary range.
CREATE UNIQUE INDEX IF NOT EXISTS uq_episodic_user_source_range
    ON episodic_memories(user_id, source_message_range)
    WHERE source_message_range IS NOT NULL;
