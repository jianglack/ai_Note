-- Phase 6: backfill legacy semantic memories for governed memory reads.
-- Additive/idempotent updates only; legacy rows remain readable by old paths.

UPDATE semantic_memories
SET status = COALESCE(status, 'active'),
    scope = COALESCE(scope, 'user'),
    access_count = COALESCE(access_count, 0);

UPDATE semantic_memories
SET memory_type = LOWER(category)
WHERE (memory_type IS NULL OR memory_type = '' OR memory_type = 'semantic')
  AND category IS NOT NULL
  AND category <> '';

UPDATE semantic_memories
SET memory_type = 'semantic'
WHERE memory_type IS NULL OR memory_type = '';

UPDATE semantic_memories
SET source = 'legacy_ai_extracted'
WHERE source IS NULL OR source = '' OR source = 'ai_extracted';

UPDATE semantic_memories
SET content_hash = md5(COALESCE(user_id, '') || ':' || COALESCE(content, ''))
WHERE content_hash IS NULL
  AND content IS NOT NULL;

UPDATE semantic_memories
SET metadata_json = '{"legacy": true, "provenance": "pre_governance_migration"}'
WHERE metadata_json IS NULL
  AND source = 'legacy_ai_extracted'
  AND source_trace_id IS NULL
  AND source_message_ids IS NULL
  AND source_tool_call_id IS NULL;
