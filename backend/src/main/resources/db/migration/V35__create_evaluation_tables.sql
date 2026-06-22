-- V22: RAG/Agent 评估体系

-- 评估数据集
CREATE TABLE eval_datasets (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id),
    name TEXT NOT NULL,
    description TEXT,
    dataset_type TEXT NOT NULL,
    item_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- 评估数据项
CREATE TABLE eval_items (
    id TEXT PRIMARY KEY,
    dataset_id TEXT NOT NULL REFERENCES eval_datasets(id) ON DELETE CASCADE,
    question TEXT NOT NULL,
    expected_answer TEXT,
    expected_contexts TEXT[],
    expected_tool_calls JSONB,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP NOT NULL
);

-- 评估运行记录
CREATE TABLE eval_runs (
    id TEXT PRIMARY KEY,
    dataset_id TEXT NOT NULL REFERENCES eval_datasets(id),
    user_id TEXT NOT NULL REFERENCES users(id),
    run_type TEXT NOT NULL,
    status TEXT DEFAULT 'running',
    config JSONB DEFAULT '{}',
    summary JSONB,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    error_message TEXT
);

-- 评估结果
CREATE TABLE eval_results (
    id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL REFERENCES eval_runs(id) ON DELETE CASCADE,
    item_id TEXT NOT NULL REFERENCES eval_items(id),
    faithfulness FLOAT,
    answer_relevancy FLOAT,
    context_precision FLOAT,
    context_recall FLOAT,
    task_completion FLOAT,
    tool_accuracy FLOAT,
    consistency FLOAT,
    latency_ms INTEGER,
    total_tokens INTEGER,
    actual_answer TEXT,
    actual_contexts JSONB,
    actual_tool_calls JSONB,
    evaluation_details JSONB,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_eval_items_dataset_id ON eval_items(dataset_id);
CREATE INDEX idx_eval_results_run_id ON eval_results(run_id);
CREATE INDEX idx_eval_runs_dataset_id ON eval_runs(dataset_id);
CREATE INDEX idx_eval_runs_user_id ON eval_runs(user_id);
