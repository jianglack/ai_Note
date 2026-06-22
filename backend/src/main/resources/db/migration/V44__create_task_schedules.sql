-- V44: 定时任务调度表
CREATE TABLE task_schedules (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    plan_template_json JSONB NOT NULL,
    original_query TEXT NOT NULL,
    trigger_type VARCHAR(20) NOT NULL DEFAULT 'ONCE',  -- ONCE, DAILY, WEEKLY, MONTHLY
    cron_expression VARCHAR(100),
    scheduled_time TIMESTAMP,
    enabled BOOLEAN DEFAULT TRUE,
    max_run_count INTEGER,
    run_count INTEGER DEFAULT 0,
    last_run_at TIMESTAMP,
    next_run_at TIMESTAMP,
    last_plan_id VARCHAR(36),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_task_schedules_user ON task_schedules(user_id);
CREATE INDEX idx_task_schedules_next_run ON task_schedules(next_run_at) WHERE enabled = TRUE;
