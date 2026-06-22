-- 创建用户画像表
CREATE TABLE user_profiles (
    user_id VARCHAR(255) PRIMARY KEY,
    
    -- 基本偏好
    preferred_note_style VARCHAR(20),
    preferred_time_format VARCHAR(10),
    default_folder_id VARCHAR(255),
    default_folder_name VARCHAR(255),
    
    -- 行为统计
    total_notes_created INTEGER NOT NULL DEFAULT 0,
    total_searches INTEGER NOT NULL DEFAULT 0,
    total_notes_deleted INTEGER NOT NULL DEFAULT 0,
    most_used_tags TEXT,
    frequent_folders TEXT,
    
    -- 时间习惯
    active_hours TEXT,
    preferred_reminder_time VARCHAR(10),
    last_active_at TIMESTAMP,
    
    -- 内容偏好
    topic_interests TEXT,
    writing_style VARCHAR(20),
    avg_note_length INTEGER,
    
    -- AI 提取的偏好
    extracted_preferences TEXT,
    
    -- 元数据
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- 创建索引
CREATE INDEX idx_user_profiles_last_active ON user_profiles(last_active_at);

-- 添加注释
COMMENT ON TABLE user_profiles IS '用户画像表 - 记录用户的长期偏好、习惯和行为统计';
COMMENT ON COLUMN user_profiles.user_id IS '用户ID';
COMMENT ON COLUMN user_profiles.preferred_note_style IS '偏好的笔记风格：markdown, plain, code';
COMMENT ON COLUMN user_profiles.total_notes_created IS '创建的笔记总数';
COMMENT ON COLUMN user_profiles.most_used_tags IS '最常用的标签（JSON数组）';
COMMENT ON COLUMN user_profiles.writing_style IS '写作风格：concise（简洁）, detailed（详细）';
