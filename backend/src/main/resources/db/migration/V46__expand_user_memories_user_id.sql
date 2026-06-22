-- Allow plan-scoped chat memory keys such as plan:{uuid}:step:{n}.
ALTER TABLE user_memories ALTER COLUMN user_id TYPE VARCHAR(128);
