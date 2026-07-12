-- Human review workflow for memory feedback.
-- Additive migration: production feedback becomes a durable queue before it can
-- be promoted into replay evaluation data.

CREATE TABLE IF NOT EXISTS memory_review_cases (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    memory_id BIGINT,
    feedback_type VARCHAR(40) NOT NULL,
    user_comment TEXT,
    expected_content TEXT,
    expected_memory_type VARCHAR(40),
    expected_capture_allowed BOOLEAN,
    status VARCHAR(40) NOT NULL DEFAULT 'pending_review',
    reviewer_id VARCHAR(128),
    reviewer_decision VARCHAR(40),
    reviewer_comment TEXT,
    replay_case_id VARCHAR(128),
    replay_case_json TEXT,
    manifest_json TEXT,
    memory_before_json TEXT,
    source_context_json TEXT,
    policy_snapshot_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    reviewed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_memory_review_cases_status_created
    ON memory_review_cases(status, created_at);

CREATE INDEX IF NOT EXISTS idx_memory_review_cases_user_created
    ON memory_review_cases(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_memory_review_cases_memory_id
    ON memory_review_cases(memory_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_memory_review_cases_replay_case_id
    ON memory_review_cases(replay_case_id)
    WHERE replay_case_id IS NOT NULL;
