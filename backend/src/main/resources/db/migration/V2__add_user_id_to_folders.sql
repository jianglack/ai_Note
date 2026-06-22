-- 为 folders 表添加 user_id 字段
ALTER TABLE folders ADD COLUMN IF NOT EXISTS user_id TEXT REFERENCES users(id);

-- 将现有文件夹关联到默认用户（如果存在）
UPDATE folders SET user_id = (SELECT id FROM users WHERE username = 'default' LIMIT 1)
WHERE user_id IS NULL;
