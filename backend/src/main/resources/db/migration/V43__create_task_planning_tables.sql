-- V43: Multi-step planning engine tables

CREATE TABLE task_plans (
    id              VARCHAR(36) PRIMARY KEY,
    user_id         VARCHAR(36) NOT NULL,
    original_query  TEXT NOT NULL,
    goal            TEXT,
    status          VARCHAR(30) NOT NULL DEFAULT 'PLANNING',
    plan_json       JSONB,
    total_steps     INTEGER DEFAULT 0,
    completed_steps INTEGER DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_task_plans_user_status ON task_plans(user_id, status);

CREATE TABLE task_steps (
    id              VARCHAR(36) PRIMARY KEY,
    plan_id         VARCHAR(36) NOT NULL REFERENCES task_plans(id) ON DELETE CASCADE,
    step_order      INTEGER NOT NULL,
    action          VARCHAR(100) NOT NULL,
    description     TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    input_params    JSONB,
    output_result   JSONB,
    compensation    JSONB,
    depends_on      INTEGER[],
    retry_count     INTEGER DEFAULT 0,
    max_retries     INTEGER DEFAULT 3,
    error_message   TEXT,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_task_steps_plan_order ON task_steps(plan_id, step_order);
