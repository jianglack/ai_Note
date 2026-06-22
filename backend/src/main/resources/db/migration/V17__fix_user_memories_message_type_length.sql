-- 修复 message_type 列长度：TOOL_EXECUTION_RESULT 需要 21 字符，原 VARCHAR(20) 不够
ALTER TABLE user_memories ALTER COLUMN message_type TYPE VARCHAR(30);
