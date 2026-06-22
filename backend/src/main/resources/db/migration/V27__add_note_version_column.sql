-- Optimistic locking: add version column to notes table
ALTER TABLE notes ADD COLUMN version INTEGER NOT NULL DEFAULT 0;
