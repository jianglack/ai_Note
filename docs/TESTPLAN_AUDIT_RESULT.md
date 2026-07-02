# TEST_PLAN.md 审核结果（四轮审核）

> 2026-06-24~27 ｜ 多轮多 agent 审核 + 逐条对抗复核 ｜ 状态：**四轮累计 31 条确认问题（0 误报），已全部修入文档**

## 累计计数
- 第一/二轮（wf_15c35291-500）：12 条确认，0 误报。
- 第四轮（wf_c51e3bbe-477）：19 条确认，0 误报。
- **总计 31 条确认，全部已修入 `docs/TEST_PLAN.md`**。
- 已完成维度：`ai-depth`、`status-sampling`、`numbers`、`frontend`、`rtm-consistency`、`impl-spec`、`consistency`。

## 第一轮确认（4，已修）
1. [broken-ref] `MindMapView` → 实为 `MindMapCanvas`（§3.10/§11.6）。✅修
2. [contradiction] §3.10「~36」vs §11.6「~35」→ 统一 ~35。✅修
3. [omission] §3.17 漏 `utils/notes.ts`(resolveNoteId) 与根级 `src/api.ts` → 补行。✅修
4. [status-mismatch] `/spirit/suggest-tags`、`/classify` 标 ◐ AiServiceTest，实际该测试只测 getChatHistory → 改 ⏳。✅修

## 第二轮确认（8，已修）
5. [impl-spec] §11.5c `TaskScheduleServiceTest` 指向不存在的 service（CRUD 在 TaskScheduleController）→ 改 `TaskScheduleControllerTest`。✅修
6. [impl-spec] §11.5c `WikilinkServiceTest` 指向不存在的 service（逻辑在 KnowledgeGraphService）→ 改扩 `KnowledgeGraphServiceTransactionTest`。✅修
7. [impl-spec] §11.5d 把 `SemanticMemory` 当 JSONB，实为 `@JdbcTypeCode(VECTOR)`；真正 JSON：AgentTrace/SideEffectJournal/TaskStep → 改正（连带 §3.15 三处：JSONB「4→3 实体」、SemanticMemory 行、小结「2/4→2/3」）。✅修
8. [contradiction] §3.13-A 表头「AiController 11 个」vs §3.14「15」；源码实为 **15**，且 §3.13-A 漏列 `/action-feedback`、`/rag-feedback`、`/generate-canvas` → 改 15 + 补 3 端点。✅修
9. [broken-ref] §3.14-B 迁移行「见 §11.3 MIG」（§11.3 实为 AI 端点）→ 改「见 §4 TPS-4 / §3.4」。✅修
10. [contradiction] §3.13 小结「4 个 AI 端点」实列 5（且现补到 8）→ 改 8。✅修
11. [contradiction] §8「关键缺陷修复率 3/4」与 §7.3 台账（B/C 共 3、2 关）不符 → 改 2/3。✅修
12. [stale] §11 编号无 11.5a/b 却直接 11.5c、11.6b 排在 11.6 前 → §11.0 加编号说明。✅修

## 第四轮确认（19，已修）—— wf_c51e3bbe-477（2026-06-27）
13. [HIGH·AI-LINK-01] §3.13-K ResilientLlmService 描述为"重试+回退"，实际源码仅用 CircuitBreaker 无 Retry；H-rag2 误归因于本类（应属 ResilientChatModel）→ 改"熔断+回退"、移除 H-rag2 归因；§11.5e 测试规格同步修正。✅修
14. [MEDIUM·AI-LINK-02] RTM 遗漏 H-rag2 行 → 补入 ⏳ 行。✅修
15. [MEDIUM·AI-LINK-03] AI 端点摘要写"8 个"实为 9 个（漏 suggest-tags/classify，误含 traces/stats ◐）→ 改 9 + 修正列表。✅修
16. [MEDIUM·AI-LINK-04] §11.3 缺 4 个端点实现规格（suggest-tags/classify/action-feedback/generate-canvas）→ 补 4 行。✅修
17. [LOW·AI-LINK-05] ResilientLlmService 漏列 isRerankAvailable → 补入。✅修
18. [HIGH·STA-01] POST /chat/stream 标 ✅ 实际仅测超时计算 → 改 ◐。✅修
19. [HIGH·STA-02] CancellationToken 引用错误测试文件（AiControllerSseTimeoutTest→实为 ToolExecutionPipelineTest）→ 修正引用。✅修
20. [MEDIUM·STA-03] AgentService 标 ✅ 但 ThreadLocal 隔离仅部分覆盖 → 改 ◐。✅修
21. [LOW·STA-04] SecurityUtils 已在独立行标 ◐（此前修复已生效）→ 无需修改（确认正确）。✅
22. [MEDIUM·NUM-1] §1.1"7 CRITICAL"但 RTM 仅列 5 个，C4/C7 缺失 → RTM 补 C4/C7 作废行。✅修
23. [HIGH·FE-COV-1] §3.10"10 已测"实际仅 4 组件有行为渲染测试 → 改"4 组件行为渲染测试"。✅修
24. [MEDIUM·FE-COV-2] src/TrashView.tsx 未纳入任何覆盖矩阵 → §3.17 补行。✅修
25. [MEDIUM·FE-COV-3] src/App.tsx（330行，13 懒加载视图）未纳入覆盖 → §3.17 补行。✅修
26. [MEDIUM·FE-COV-4] §3.17 标 ⏳ 的 useMeasuredHeight 和 utils/notes.ts 在 §11.6b 无实现规格 → 补入 P3。✅修
27. [LOW·FE-COV-5] 3 个 Tiptap 扩展 .ts 文件（WikiLink/Annotation/SlashCommand）不在任何覆盖矩阵 → §3.17 补 Tiptap 扩展行。✅修
28. [LOW·FE-COV-6] src/main.tsx ErrorBoundary/PrivateRoute 未纳入覆盖 → §3.17 补行。✅修
29. [MEDIUM·RTM-TSR-001] C4/C7 缺失（同 NUM-1）→ 已修。✅修
30. [HIGH·RTM-TSR-002] H-rag2 缺 RTM 行（同 AI-LINK-02）→ 已修。✅修
31. [HIGH·RTM-TSR-003] §9.2 TSR 声称"全部 CRITICAL/HIGH 已覆盖"但 H-rag2 ⏳ → 修正措辞，明确列出未覆盖项。✅修

## 主循环补充自检（历史记录）
- **引用测试存在性**：文档引用的所有 `*Test/*IT` 中，凡标"现有测试(✅/◐)"者均真实存在；其余皆为 §11 规定待建的 ⏳ 测试。除 #5/#6 外无失效引用。
- **完整性**（前序回合）：service/agent/controller/config/chunking/filter/security/util 共 155 实质类，0 遗漏。
