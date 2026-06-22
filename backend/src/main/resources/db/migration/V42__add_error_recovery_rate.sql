-- Add error_recovery_rate column for Agent evaluation
ALTER TABLE eval_results ADD COLUMN IF NOT EXISTS error_recovery_rate DOUBLE PRECISION;
