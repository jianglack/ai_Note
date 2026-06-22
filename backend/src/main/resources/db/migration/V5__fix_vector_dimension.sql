-- Fix vector dimension to match GLM embedding-2 (1024 dimensions)
ALTER TABLE embeddings ALTER COLUMN vector TYPE vector(1024);
