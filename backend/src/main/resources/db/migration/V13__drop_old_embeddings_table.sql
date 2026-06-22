-- V13: 删除旧的 embeddings 表
-- LangChain4j 使用 langchain4j_embeddings 表，不再需要旧表

DROP TABLE IF EXISTS embeddings;
