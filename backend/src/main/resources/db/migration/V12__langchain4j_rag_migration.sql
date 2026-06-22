-- V12: LangChain4j RAG 重构
-- 清空旧的 embeddings 数据，为 LangChain4j 做准备

-- 1. 清空旧的 embeddings 表数据
TRUNCATE TABLE embeddings;

-- 2. LangChain4j 的 PgVectorEmbeddingStore 会自动创建 langchain4j_embeddings 表
-- 表结构包含：embedding_id (UUID), embedding (vector), text (TEXT), metadata (JSON)

-- 3. 保留旧表结构以便回退（如果需要）
-- 如果将来要删除旧表，可以执行：
-- DROP TABLE IF EXISTS embeddings;

-- 注意：应用启动后需要重新生成所有笔记的 embedding
-- 可以通过后台维护任务或手动脚本触发
