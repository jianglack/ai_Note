-- V28: AI Workflows/Automation

CREATE TABLE ai_workflows (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id),
    name TEXT NOT NULL,
    description TEXT,
    trigger_type TEXT NOT NULL,
    trigger_config JSONB NOT NULL,
    steps JSONB NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    last_run_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE ai_workflow_runs (
    id TEXT PRIMARY KEY,
    workflow_id TEXT NOT NULL REFERENCES ai_workflows(id) ON DELETE CASCADE,
    status TEXT DEFAULT 'running',
    results JSONB,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    error TEXT
);

CREATE INDEX idx_ai_workflows_user_id ON ai_workflows(user_id);
CREATE INDEX idx_ai_workflow_runs_workflow_id ON ai_workflow_runs(workflow_id);
