-- V50: Persist plan step side-effect journals outside task_steps.

CREATE TABLE side_effect_journal (
    id              VARCHAR(36) PRIMARY KEY,
    plan_id         VARCHAR(36) NOT NULL REFERENCES task_plans(id) ON DELETE CASCADE,
    step_id         VARCHAR(36) NOT NULL REFERENCES task_steps(id) ON DELETE CASCADE,
    step_order      INTEGER NOT NULL,
    version         INTEGER NOT NULL DEFAULT 1,
    original_action VARCHAR(100) NOT NULL,
    rollback_action VARCHAR(100),
    resource_id     VARCHAR(100),
    executable      BOOLEAN NOT NULL DEFAULT false,
    reason          TEXT,
    output_snapshot JSONB,
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    journal_json    JSONB NOT NULL,
    rollback_result JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    executed_at     TIMESTAMP
);

CREATE INDEX idx_side_effect_journal_plan ON side_effect_journal(plan_id);
CREATE INDEX idx_side_effect_journal_step ON side_effect_journal(step_id);
CREATE INDEX idx_side_effect_journal_plan_step ON side_effect_journal(plan_id, step_id, created_at DESC);
