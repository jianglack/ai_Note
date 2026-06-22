-- 日程表
CREATE TABLE schedules (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT,
    user_id TEXT NOT NULL REFERENCES users(id),
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    all_day BOOLEAN DEFAULT FALSE,
    rrule TEXT,
    reminder_minutes INTEGER,
    status TEXT DEFAULT 'pending',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- 日程-笔记关联表
CREATE TABLE schedule_notes (
    schedule_id TEXT NOT NULL REFERENCES schedules(id) ON DELETE CASCADE,
    note_id TEXT NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    PRIMARY KEY (schedule_id, note_id)
);

-- 索引
CREATE INDEX idx_schedules_user_id ON schedules(user_id);
CREATE INDEX idx_schedules_start_time ON schedules(start_time);
CREATE INDEX idx_schedules_status ON schedules(status);
