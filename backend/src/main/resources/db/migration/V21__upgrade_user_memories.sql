-- V21: Upgrade user_memories for full tool-call fidelity
-- Part of context engineering refactor (anti-hallucination)

ALTER TABLE user_memories ADD COLUMN tool_calls_json TEXT;
ALTER TABLE user_memories ADD COLUMN tool_call_id VARCHAR(64);

-- Clear polluted history that has lost tool_calls data.
-- Using DELETE (not TRUNCATE) because DELETE is transactional and rollback-safe.
DELETE FROM user_memories;
