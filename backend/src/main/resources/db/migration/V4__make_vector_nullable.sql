-- Make vector column nullable to support PENDING status
ALTER TABLE embeddings ALTER COLUMN vector DROP NOT NULL;
