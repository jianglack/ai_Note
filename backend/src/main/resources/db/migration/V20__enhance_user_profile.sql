-- 添加用户画像增强字段
-- 为用户画像表添加新的偏好字段

ALTER TABLE user_profiles
ADD COLUMN IF NOT EXISTS preferred_response_style VARCHAR(20),
ADD COLUMN IF NOT EXISTS expertise VARCHAR(20),
ADD COLUMN IF NOT EXISTS preferred_language VARCHAR(10),
ADD COLUMN IF NOT EXISTS enable_code_examples BOOLEAN DEFAULT false;

-- 添加注释
COMMENT ON COLUMN user_profiles.preferred_response_style IS '偏好的响应风格：detailed（详细）, concise（简洁）, technical（技术性）';
COMMENT ON COLUMN user_profiles.expertise IS '专业水平：beginner（初学者）, intermediate（中级）, expert（专家）';
COMMENT ON COLUMN user_profiles.preferred_language IS '偏好语言：zh（中文）, en（英文）';
COMMENT ON COLUMN user_profiles.enable_code_examples IS '是否喜欢代码示例';

-- 为现有用户设置默认值
UPDATE user_profiles
SET 
    preferred_response_style = 'detailed',
    expertise = 'intermediate',
    preferred_language = 'zh',
    enable_code_examples = true
WHERE preferred_response_style IS NULL;
