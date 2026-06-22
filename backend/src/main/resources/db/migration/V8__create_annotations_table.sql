-- 创建注释表
CREATE TABLE IF NOT EXISTS annotations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    note_id TEXT NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    user_id VARCHAR(255) NOT NULL,
    text_content TEXT NOT NULL,
    comment TEXT,
    start_offset INT NOT NULL,
    end_offset INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建注释标签关联表
CREATE TABLE IF NOT EXISTS annotation_tags (
    annotation_id UUID REFERENCES annotations(id) ON DELETE CASCADE,
    tag_id TEXT REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (annotation_id, tag_id)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_annotations_note_id ON annotations(note_id);
CREATE INDEX IF NOT EXISTS idx_annotations_user_id ON annotations(user_id);
CREATE INDEX IF NOT EXISTS idx_annotation_tags_annotation_id ON annotation_tags(annotation_id);
CREATE INDEX IF NOT EXISTS idx_annotation_tags_tag_id ON annotation_tags(tag_id);
