-- V32: Extend user profiling for personalized learning
ALTER TABLE semantic_memories ADD COLUMN IF NOT EXISTS domain VARCHAR(50);
ALTER TABLE semantic_memories ADD COLUMN IF NOT EXISTS weight DOUBLE PRECISION DEFAULT 1.0;

COMMENT ON COLUMN semantic_memories.domain IS 'Knowledge domain: tech/life/work/study/health/finance';
COMMENT ON COLUMN semantic_memories.weight IS 'Importance weight for context ranking';
