-- 成本追踪字段
ALTER TABLE agent_traces ADD COLUMN IF NOT EXISTS estimated_cost_yuan DECIMAL(10,6) DEFAULT 0;
ALTER TABLE agent_traces ADD COLUMN IF NOT EXISTS call_type VARCHAR(20) DEFAULT 'AGENT';
-- call_type: AGENT / CHAT / EMBEDDING / RERANK / MEMORY_EXTRACT

CREATE INDEX IF NOT EXISTS idx_agent_traces_created_at ON agent_traces(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_agent_traces_call_type ON agent_traces(call_type);
