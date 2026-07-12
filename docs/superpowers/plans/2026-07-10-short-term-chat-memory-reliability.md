# 短期对话记忆可靠性实施计划

## 阶段 1：约束与配置

- [ ] 新增 V60 迁移：空序号回填、唯一约束、`trimmed_at`、head、compaction job 和索引。
- [ ] 扩展 `MemoryProperties.ChatHistory`，增加模型窗口、flush retry 和 compaction 参数。
- [ ] 增加 migration/schema 单元测试。

## 阶段 2：有界模型窗口与并发追加

- [ ] 先补失败测试：禁止全量查询、窗口上限、跨实例序号分配、裁剪不删除。
- [ ] 新增 `ChatMemoryHead` 及 repository 行锁。
- [ ] 重构 `ReliableChatMemoryStore`：有界尾部读取、持久事务重试、唯一序号追加、trim 标记。
- [ ] 修复 `/chat/save` 的空 sequence 和双消息原子追加。
- [ ] 让清空链路同步清理 head 与未完成 compaction job。

## 阶段 3：持久压缩闭环

- [ ] 新增 compaction job entity/repository/worker/metrics。
- [ ] 裁剪事务内幂等创建范围任务并推进 checkpoint。
- [ ] 增加同步摘要入口，写入 `source_message_range`。
- [ ] 实现任务领取租约、指数退避、最大重试和 dead letter。
- [ ] 测试重复触发、模型失败恢复、租约恢复和原始历史保留。

## 阶段 4：历史与模型边界验证

- [ ] 修正历史 cursor/order 的稳定性测试。
- [ ] 验证 trimmed USER/AI 仍出现在 UI 分页，且不进入模型窗口。
- [ ] 验证 tool-call 边界被裁剪时不会构造非法模型消息序列。
- [ ] 清空前摘要改为有界读取。

## 阶段 5：压测与门禁

- [ ] PostgreSQL 集成测试：10,000 行历史、有界读取和索引计划。
- [ ] PostgreSQL 集成测试：32 并发追加、唯一序号和零丢失。
- [ ] 故障注入：flush 瞬时/永久失败、compaction 前两次失败后恢复。
- [ ] 生成机器可读 JSON 报告，包含状态、样本量、p50/p95、失败数和 gate。
- [ ] 执行 focused tests、`mvn test`、Docker IT、前端测试、前端 build、`git diff --check`。
- [ ] 重启最新后端并做真实 API 分页与清空范围 smoke test。

## 最终复审

- [ ] 对照任务 8 五项原始要求逐条给出代码、测试、运行报告证据。
- [ ] 区分“已证明的本地门禁”和“仍需生产容量规划的 SLA”。
- [ ] 记录回滚配置、迁移不可逆点和剩余风险。
