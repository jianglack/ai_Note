-- Fix vector dimension to match GLM embedding-2 (1024 dimensions)
DROP INDEX IF EXISTS idx_embeddings_vector;
ALTER TABLE embeddings ALTER COLUMN vector TYPE vector(1024);
