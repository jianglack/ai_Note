-- 创建 Agent 任务状态表
CREATE TABLE agent_task_states (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    session_id VARCHAR(255) NOT NULL,
    
    -- 任务信息
    task_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    goal TEXT,
    
    -- 步骤管理
    completed_steps TEXT,
    pending_steps TEXT,
    current_step VARCHAR(200),
    
    -- 结果存储
    intermediate_results TEXT,
    final_result TEXT,
    error_message TEXT,
    
    -- 优先级
    priority VARCHAR(10) DEFAULT 'medium',
    
    -- 时间戳
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- 创建索引
CREATE INDEX idx_agent_task_states_user_id ON agent_task_states(user_id);
CREATE INDEX idx_agent_task_states_session_id ON agent_task_states(session_id);
CREATE INDEX idx_agent_task_states_status ON agent_task_states(status);
CREATE INDEX idx_agent_task_states_created_at ON agent_task_states(created_at DESC);

-- 添加注释
COMMENT ON TABLE agent_task_states IS 'Agent 任务状态表 - 外化 Agent 的工作状态';
COMMENT ON COLUMN agent_task_states.task_type IS '任务类型：create_note, search, organize, schedule, multi_step';
COMMENT ON COLUMN agent_task_states.status IS '任务状态：pending, in_progress, completed, failed';
COMMENT ON COLUMN agent_task_states.completed_steps IS '已完成的步骤（JSON数组）';
COMMENT ON COLUMN agent_task_states.pending_steps IS '待完成的步骤（JSON数组）';
COMMENT ON COLUMN agent_task_states.intermediate_results IS '中间结果（JSON）';
