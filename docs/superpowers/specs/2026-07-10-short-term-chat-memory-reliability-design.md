# 短期对话记忆可靠性与压测设计

## 1. 目标

任务 8 要证明并实现以下生产性质：

1. 模型调用不会读取或注入全量聊天历史。
2. 短期消息采用稳定追加语义，不以全量删除重写作为默认路径。
3. 同一用户的并发写入在多线程、多实例下不丢失、不覆盖、不产生重复序号。
4. 用户可见历史与模型可见窗口使用不同查询边界；模型裁剪不删除用户历史。
5. 长对话裁剪只压缩尚未处理的区间，任务幂等、可重试、可恢复、可观测。
6. 容量、并发、故障注入和长对话测试形成可重复的发布门禁。

## 2. 当前审核结论

### P0/P1 风险

- `ReliableChatMemoryStore.getMessages()` 在每次 Agent 调用前读取用户全部 `user_memories`。
- append 模式仍先读取全量历史并在应用内计算重叠，数据量增长后为 O(n) 热路径。
- `sequence_number` 只有普通索引，没有非空约束和唯一约束；两个实例可读取相同最大序号并写出冲突顺序。
- `ConcurrencyGuard` 是 JVM 内存锁，只能限制单实例请求，不能作为数据库完整性边界。
- `/chat/save` 写入的消息没有 `sequence_number`，当前生产库已经存在这类空序号数据。
- TokenWindow 裁剪后没有持久化裁剪检查点；同一旧前缀可能被重复提交给摘要模型。
- 摘要是无持久任务状态的异步调用，失败后没有租约、重试、死信或明确证据。

### 已有可复用能力

- LangChain4j `TokenWindowChatMemory` 已提供最终 token 上限。
- UI 历史已经使用有上限的 cursor pagination，默认 100、最大 200。
- append/legacy rewrite 已有配置开关。
- Agent 调用已具备 deferred flush 和失败降级标志。
- Micrometer、Prometheus、Testcontainers PostgreSQL 已存在。

## 3. 设计原则

- 数据库约束是最终一致性底线，JVM 锁只是削峰手段。
- UI 历史完整保留，模型窗口严格有界，两者不能共享“读取全部”的接口。
- 裁剪是状态转换，不是删除；只有用户明确清空或保留策略才能删除历史。
- 压缩任务必须先持久化再异步执行，摘要模型不可用不能造成区间永久丢失。
- 所有阈值通过配置管理，默认值可回滚，不写死为不可调规则。

## 4. 数据模型

### 4.1 `user_memories`

新增：

- `trimmed_at`: 非空表示该消息不再进入模型短期窗口，但仍可在 UI 历史中分页展示。
- `sequence_number` 改为强制非空；迁移时对历史空值按用户和创建顺序续排。
- 唯一约束 `(user_id, sequence_number)`，防止跨实例重复序号。
- 模型查询索引 `(user_id, trimmed_at, sequence_number DESC)`。

### 4.2 `chat_memory_heads`

每用户一行：

- `user_id` 主键。
- `next_sequence_number`：下一可分配序号。
- `last_compaction_enqueued_sequence`：已进入持久压缩任务的最高序号。
- `version`、`updated_at`：审计与诊断。

写入事务先确保 head 存在，再 `PESSIMISTIC_WRITE` 锁定，完成序号分配和 head 推进。

### 4.3 `chat_memory_compaction_jobs`

持久任务字段：

- 用户、起止 sequence、状态、尝试次数、下次尝试时间。
- processing 租约时间、最近错误、创建/完成时间。
- `(user_id, from_sequence, to_sequence)` 唯一，保证范围幂等。

状态：`pending -> processing -> completed`；失败进入延迟重试，超过上限进入 `dead_letter`。过期 processing 租约允许重新领取。

## 5. 读写链路

### 5.1 模型读取

1. 只查询 `trimmed_at IS NULL` 的最新 `model-window-max-messages` 条。
2. 数据库结果恢复为正序。
3. 清除孤立 tool result / tool call，保证请求协议有效。
4. 再由 `TokenWindowChatMemory` 按 `max-memory-tokens` 做最终 token 裁剪。

因此消息条数和 token 数均有硬上限，任何用户历史规模都不会导致全量模型加载。

### 5.2 追加写入

1. deferred buffer 只保留本轮最新窗口快照。
2. flush 在独立事务中锁定 `chat_memory_heads`。
3. 读取有界活动尾部，计算 persisted suffix 与 incoming prefix 的重叠。
4. 重叠前缀标记 `trimmed_at`，不删除。
5. 新消息使用 head 分配的唯一 sequence 批量追加。
6. 更新 head；事务提交后才算持久化成功。

`legacy_rewrite` 继续保留为紧急回滚路径，但默认和发布门禁都使用 append。

### 5.3 UI 历史

- UI 只读取 USER/AI 类型，不过滤 `trimmed_at`。
- cursor API 最大 200 条，始终分页。
- 模型读取方法和 UI 分页方法名称、查询和测试完全分开。
- 模型裁剪不会改变 UI 可见历史条数。

### 5.4 压缩

1. 新裁剪区间累计达到最小用户轮数后，在同一写事务中创建 compaction job。
2. head 的 enqueued checkpoint 与 job 同事务提交，避免重复或遗漏。
3. worker 使用短事务领取任务，不在数据库事务中等待 LLM。
4. worker 按 sequence 范围读取有限批次，生成带 `source_message_range` 的 episodic memory。
5. 成功完成；失败记录错误并退避重试；租约超时可恢复。

## 6. 故障语义

- flush 对瞬时异常按配置进行有限重试，每次重试使用新事务。
- 全部尝试失败时响应标记 degraded，不宣称该轮历史已持久化。
- 唯一约束冲突不能静默吞掉；必须重试整个锁定事务或明确失败。
- 压缩失败不影响原始历史，原始消息仍保留，可由 worker 重试。
- 清空历史同时删除模型窗口、head 和未完成压缩任务；清空前摘要仍使用有界输入。

## 7. 可观测性

Prometheus 指标：

- `chat_memory_load_total`、`chat_memory_loaded_messages`、`chat_memory_load_latency`
- `chat_memory_append_total`、`chat_memory_appended_messages`
- `chat_memory_trimmed_messages`
- `chat_memory_flush_retry_total`、`chat_memory_flush_failure_total`
- `chat_memory_compaction_jobs_total{result=...}`
- `chat_memory_compaction_latency`

日志只记录 user hash/数量/区间/状态，不记录聊天正文。

## 8. 发布门禁

硬门禁：

- 10,000 条历史下模型读取行数不超过配置上限。
- 32 并发写入无丢失、无重复 sequence、顺序连续。
- UI 分页可读取全部 USER/AI 历史，模型窗口仅返回有界未裁剪行。
- 同一裁剪区间最多一个 compaction job。
- 前两次摘要失败、第三次成功时任务最终 completed，原始历史完整。
- flush 瞬时失败后重试成功；永久失败明确 degraded。
- 全量后端测试、迁移回放、schema validation、前端测试和构建通过。

性能基线：

- 10,000 行历史的模型窗口查询 p95 < 100 ms（本地 PostgreSQL 基线）。
- 32 并发追加完成时间和吞吐写入报告，不把单机数值直接宣传为生产 SLA。

## 9. 非目标

- 本轮不引入多会话 UI 或 thread 切换产品设计；现有 memory id 仍为 user id。
- 本轮不删除 `legacy_rewrite` 回滚开关。
- 本轮不把 UI transcript 迁移到独立物理表；通过 `trimmed_at` 和独立查询面实现读写职责隔离。若未来支持多 thread，再迁移为独立 `chat_messages` 表。
