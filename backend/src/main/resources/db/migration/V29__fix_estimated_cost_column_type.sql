-- 修正 estimated_cost_yuan 列类型
-- V26 创建时使用 DECIMAL(10,6)（numeric），但 Java 实体使用 Double 类型
-- Hibernate 6 期望 DOUBLE PRECISION (float8)，导致 schema validation 失败
ALTER TABLE agent_traces ALTER COLUMN estimated_cost_yuan TYPE DOUBLE PRECISION;
