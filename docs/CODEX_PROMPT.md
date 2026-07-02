# Codex 测试实现任务提示词

## 任务

你是 AI Note 项目的测试工程师。请按照 `docs/TEST_PLAN.md` 第十一部分（§11）的实现规格，为项目编写全部待实现测试（标记为 ⏳ 的项）。所有测试设计已完成，你只需编码实现，**不做设计决策**。

## 项目背景

- **技术栈**：后端 Spring Boot 3.2.5 / Java 17 / LangChain4j 1.0.0-beta3 / PostgreSQL 15 + pgvector / Redis 7；前端 React 18 / TypeScript / Vite / Zustand / Tiptap v2
- **分支**：`fix/audit-remediation`
- **后端路径**：`backend/`，前端路径：`frontend/`
- **数据库迁移**：`backend/src/main/resources/db/migration/V1..V55`（55 个）

## 核心文档

**唯一参考文档**：`docs/TEST_PLAN.md`

阅读顺序：
1. **§11.0**（强制约定）—— 风格规则、DoD、覆盖率目标
2. **§11.1**（金标准范例）—— `ScheduleActionToolTest` 完整代码，所有工具单测照此结构
3. **§11.6 开头**（前端范例 A/B）—— 前端测试两种模式的可复制范例
4. **§11.2–§11.8**（逐项实现规格）—— 每项列出目标类、依赖、场景、断言
5. **§11.9**（执行顺序）—— 分 4 阶段，严格按优先级推进
6. 对照 **§3.13–§3.17** 覆盖矩阵确认状态标记

## 执行顺序（§11.9，严格遵守）

### 阶段一（最高优先）
1. `§11.5d-P0` — `SchemaValidationIT`（@SpringBootTest + Testcontainers pgvector + ddl-auto=validate，一个测试守住 31 实体一致性）
2. `§11.2` — 5 个 Agent 工具单测（ScheduleActionTool/FolderActionTool/KnowledgeActionTool/InsightActionTool/MediaActionTool）
3. 验证：`cd backend && mvn -o test` 全绿

### 阶段二（AI 核心）
1. `§11.3` — 9 个 AI 端点补测（@WebMvcTest，含 suggest-tags/classify/action-feedback/generate-canvas）
2. `§11.4` — RAG 评估/反馈/记忆抽取
3. `§11.5` — 规划组件（Planner/StateValidator/ProgressEmitter/ScheduleRunner/Reflection 边界）
4. `§11.5e` — 遗漏服务直测（ResilientLlmService **注意是熔断非重试**、OcrService、ToolCallAuditor 等）
5. `§11.5f` — 配置/横切补测（ResilientChatModel **验证 H-rag2 重试语义**、JwtAuthenticationFilter、分块解析器等）
6. 目标：`agent/**` 与 `service.planning` 覆盖率 **≥70%**

### 阶段二·b（非 AI 业务，可与阶段二并行）
1. `§11.5c` — Graph 控制器 / 混合搜索 / 笔记长尾动作（P1）→ Canvas/MindMap/NoteDatabase 等（P2）
2. `§11.5d` 其余 — JSONB 映射、@Version 乐观锁、FK 行为、Repository 测试

### 阶段三（前端）
1. `§11.6` — 48 个前端组件补测（vitest + RTL）
2. `§11.6b` — hooks/stores/services/pages/根级文件（TrashView/App.tsx 等）
3. 目标：引入前端覆盖率门槛（statements 48%、branches 38%、functions 50%、lines 50%）

### 阶段四（E2E + 性能）
1. `§11.7` — Playwright E2E
2. `§11.8` — k6/Gatling 性能基线

## 强制风格约定（§11.0，违反会导致编译错或 flaky）

1. **不要用** `@ExtendWith(MockitoExtension.class)`（strict-stub 会抛 `UnnecessaryStubbingException`）；用 `mock()` + `@BeforeEach` 手工装配
2. `ToolExecutionPipeline` 一律 mock 成直接执行 supplier：
   ```java
   when(pipeline.execute(anyString(), anyString(), anyString(), any(JsonNode.class), any(Supplier.class)))
       .thenAnswer(inv -> inv.<Supplier<String>>getArgument(4).get());
   ```
3. 实体/model 无公开构造器时 **用 `mock()` 桩 getter**，不要 `new`
4. 断言用 AssertJ `assertThat(...)`；交互用 `verify(...)`
5. 破坏性动作：断言返回含 `PENDING_ACTION` 且 `verify(service, never())`
6. 中文文案断言用 `contains` 片段（`已创建`/`缺少`/`已存在`），不要全等
7. `Map.of(...)` 不接受 null —— 所有进入 Map.of 的 getter 必须桩非 null
8. 前端测试：vitest + @testing-library/react + userEvent；防抖用 `vi.useFakeTimers()`

## 验证命令

```bash
# 每完成一组
cd backend && mvn -q -o test -Dtest=新测试类名

# 全部后端完成后
cd backend && mvn test          # 单元测试全绿
cd backend && mvn verify        # JaCoCo 覆盖率门槛

# 前端
cd frontend && npm test
cd frontend && npm run build    # 生产构建不报错
```

## 完成判据（DoD）

- [ ] 每个新测试通过，被测分支被覆盖
- [ ] `mvn -o test` 全绿，不降低既有 536 个用例数
- [ ] §3.13–§3.17 矩阵中对应 ⏳ 项全部转 ✅
- [ ] `agent/**` 与 `service.planning` 行覆盖 ≥70%
- [ ] `mvn verify`（覆盖率门槛）通过
- [ ] 前端 `npm test` + `npm run build` 全绿

## 安全约束（绝对禁止）

1. **不要读、不要提交** `AUDIT_REPORT.md`、`FIX_PLAN.md`、`VERIFICATION_REPORT.md`（gitignore，含漏洞详情，仓库有公网 remote）
2. **迁移测试仅用临时库**（Testcontainers 或 `CREATE DATABASE ... / DROP DATABASE ...`），**严禁碰真实 `ainote` 库**
3. 测试代码中不要包含真实密钥、token 或密码

## 环境（§1.9）

| 组件 | 版本 |
|------|------|
| JDK | 17 |
| Maven | 3.9.8 |
| Docker | 29.2.1（Testcontainers 需要） |
| PostgreSQL | pgvector/pgvector:pg15 |
| Node | 与 frontend/package.json engines 对齐 |

Windows 环境下 Testcontainers 需设置：`DOCKER_HOST=npipe:////./pipe/docker_engine`

## 注意事项

- §11.5e `ResilientLlmServiceTest`：本类**仅用 CircuitBreaker（熔断）**，无 Retry。测试场景为"依赖抛异常时熔断/回退"，**不是**重试。H-rag2（重试只认 io.*）属于 `ResilientChatModel`，在 §11.5f 测试
- §11.5d-P0 `SchemaValidationIT` 是最高杠杆测试，**优先于一切，先做**
- §11.5a/§11.5b 编号不存在（非缺失），从 §11.5c 开始是正常的
- 每阶段完成后，更新 `docs/TEST_PLAN.md` 中对应 ⏳ 标记为 ✅，并更新第六部分（执行记录）和第八部分（度量）
