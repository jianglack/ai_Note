-- Agent 调用追踪表
CREATE TABLE agent_traces (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    trace_id VARCHAR(64),
    session_id VARCHAR(64),
    input_text TEXT,
    output_text TEXT,
    tools_called JSONB,
    input_tokens INTEGER DEFAULT 0,
    output_tokens INTEGER DEFAULT 0,
    total_tokens INTEGER DEFAULT 0,
    latency_ms INTEGER DEFAULT 0,
    model VARCHAR(64),
    status VARCHAR(20) DEFAULT 'SUCCESS',
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX idx_agent_traces_user_id ON agent_traces(user_id);
CREATE INDEX idx_agent_traces_created_at ON agent_traces(created_at);
CREATE INDEX idx_agent_traces_trace_id ON agent_traces(trace_id);
