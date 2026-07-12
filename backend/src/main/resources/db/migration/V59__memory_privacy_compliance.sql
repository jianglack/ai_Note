-- Memory privacy and compliance support.
-- Additive migration: admin access audit is durable and memory retention
-- operations can query deleted/retracted rows by updated_at.

CREATE TABLE IF NOT EXISTS admin_access_audits (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    action VARCHAR(128) NOT NULL,
    decision VARCHAR(32) NOT NULL,
    reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_admin_access_audits_user_time
    ON admin_access_audits(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_admin_access_audits_action_time
    ON admin_access_audits(action, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_admin_access_audits_decision_time
    ON admin_access_audits(decision, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_semantic_memories_status_updated
    ON semantic_memories(status, updated_at);
