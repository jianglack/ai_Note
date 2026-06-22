-- 修复 annotations 表 id 类型为 VARCHAR 以匹配 JPA 实体
-- 先删除外键约束
ALTER TABLE annotation_tags DROP CONSTRAINT IF EXISTS annotation_tags_annotation_id_fkey;

-- 修改 annotations.id 类型
ALTER TABLE annotations ALTER COLUMN id TYPE VARCHAR(255) USING id::VARCHAR;

-- 修改 annotation_tags.annotation_id 类型
ALTER TABLE annotation_tags ALTER COLUMN annotation_id TYPE VARCHAR(255) USING annotation_id::VARCHAR;

-- 重新添加外键约束
ALTER TABLE annotation_tags ADD CONSTRAINT annotation_tags_annotation_id_fkey
    FOREIGN KEY (annotation_id) REFERENCES annotations(id) ON DELETE CASCADE;
