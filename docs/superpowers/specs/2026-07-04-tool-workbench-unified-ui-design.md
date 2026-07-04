# Tool Workbench Unified UI Design

Date: 2026-07-04
Status: Approved for implementation planning

## Background

The left sidebar currently groups several AI and productivity tools under "视图", "工具", and "其他", but the destination pages are visually inconsistent:

- Some tools are full-screen views with their own ad hoc headers.
- Some tools still use older modal or drawer styles.
- Some tools are embedded inside the main note surface.
- Graph, timeline, and trace views are especially different from the warm paper style used by the rest of the application.

The approved direction is to keep the existing product structure and tool capabilities, but unify the presentation through one shared "Tool Workbench" shell.

## Goals

1. Make all sidebar tools feel like part of the same product.
2. Remove the duplicated AI memory entry from the main toolbar path and keep tool discovery in the sidebar/settings areas.
3. Prioritize redesign of:
   - 关系图谱
   - 时间线
   - 调用追踪
4. Put the remaining tools into the same migration plan:
   - 画布
   - 创建日程
   - 工作流
   - 任务中心
   - 定时任务
   - 评估中心
   - Agent 指标
   - 回收站
5. Preserve existing backend APIs and business behavior unless a tool already has a known bug.
6. Avoid another isolated UI system. The new shell should reuse the app's existing warm paper palette, compact controls, and restrained visual language.

## Non-Goals

- Do not redesign the note editor.
- Do not change memory, RAG, agent, workflow, or schedule backend semantics as part of this UI pass.
- Do not replace existing graph, timeline, trace, or workflow data models.
- Do not introduce a new design system dependency.
- Do not implement feature behavior while writing this design document.

## Approved Direction

Use a shared full-screen Tool Workbench for complex tools.

Common structure:

- Top bar with back button, tool title, short scope text, status chips, and primary actions.
- Left control rail for scope, filters, search, legends, and mode controls.
- Main content area for graph canvas, timeline list, trace list, dashboard cards, or table content.
- Optional right detail panel for selected nodes, records, traces, tasks, or deleted notes.
- Shared empty, loading, error, and permission states.

Create/edit actions that are short forms can use a shared Paper Dialog instead of becoming separate full-screen pages. "创建日程" is treated as a lightweight creation action, not a full-screen tool page.

## Visual Rules

The workbench should match the existing application rather than the old white/blue admin style.

Use these visual constraints:

- Warm paper backgrounds based on the current app tokens.
- 8px border radius for controls and panels unless an existing component requires otherwise.
- Subtle borders instead of heavy shadows.
- Icon buttons for close, refresh, export, filter, and view mode actions.
- Text labels for clear commands only, such as "保存", "恢复", "导出", or "运行评估".
- No nested cards inside cards.
- No black floating overlays for graph node details.
- No oversized dashboard cards that make operational tools feel like marketing pages.
- Chinese UI text for all visible labels in these tools.
- Responsive behavior must avoid overlapping toolbar text and action buttons.

## Shared Components

### ToolWorkbenchShell

Purpose:

Provide one full-screen frame for complex tools.

Responsibilities:

- Render the top bar.
- Render the optional left rail.
- Render the main content slot.
- Render the optional right detail slot.
- Own shared keyboard-safe and responsive layout behavior.

Inputs:

- `title`
- `subtitle`
- `scopeLabel`
- `statusChips`
- `primaryActions`
- `secondaryActions`
- `leftRail`
- `main`
- `detail`
- `onBack`

### ToolControlRail

Purpose:

Provide consistent filter/search/navigation structure inside tool pages.

Typical sections:

- 范围
- 搜索
- 筛选
- 状态
- 图例
- 操作

### ToolDetailPanel

Purpose:

Replace tool-specific ad hoc popovers and side details.

Rules:

- It should show only the selected object's information.
- It should not inject unrelated RAG, memory, or note content.
- Destructive actions must be visually separated and confirmed when needed.

### PaperDialog

Purpose:

Provide one consistent modal style for short creation/edit forms.

Used for:

- 创建日程
- Edit schedule
- Small workflow or task creation forms, where applicable

Rules:

- Keep the backdrop quiet.
- Use the same paper palette and 8px controls.
- Use icon close button.
- Do not force lightweight forms into full-screen navigation.

## Tool Mapping

| Sidebar Entry | Target Pattern | Notes |
| --- | --- | --- |
| 关系图谱 | Canvas workbench | First priority. Keep graph behavior, replace old overlay/detail UI with left rail and right detail panel. |
| 时间线 | List-detail workbench | First priority. Date groups in main area, filters in rail, selected event preview on the right. |
| 画布 | Canvas workbench | Keep existing list/editor modes, but move into shared shell. |
| 创建日程 | PaperDialog | Lightweight form action. Can be opened from sidebar, note context, or task context. |
| 工作流 | Dashboard workbench | Templates/status filters on the left, runs/cards in main area, run detail on the right. |
| 任务中心 | List-detail workbench | Plans/tasks in main area, task or plan detail on the right. |
| 定时任务 | List-detail workbench | Schedule status/frequency filters, list/table main area, edit/history detail panel. |
| 评估中心 | Dashboard workbench | Dataset/run filters, metrics and result comparisons in main area. |
| Agent 指标 | Dashboard workbench | Keep metrics content, but remove admin-page visual style. |
| 调用追踪 | Dashboard/list workbench | First priority. Trace filters in rail, trace list in main area, selected call chain on right. |
| 回收站 | List-detail workbench | Move out of embedded note surface into full-screen tool page with preview/actions. |

## Priority Plan

### Phase 1: Shared Shell Foundation

Create shared workbench layout and styling primitives.

Expected implementation scope:

- New shared workbench component files.
- New shared workbench CSS or integration into the existing feature CSS layer.
- No tool behavior changes.
- Add a route/state adapter if current tool navigation requires it.

Acceptance:

- A simple smoke test can render the shell with title, rail, content, and detail panel.
- The shell matches the warm paper app style at desktop width.
- Existing unrelated views still render.

### Phase 2: First-Priority Tool Migration

Migrate:

- 关系图谱
- 时间线
- 调用追踪

Acceptance:

- Each tool opens as a full-screen workbench.
- Each tool has consistent top bar, left rail, main content, and right detail behavior.
- Old black graph overlays and old trace/timeline visual language are removed.
- Existing data loading and refresh behavior still works.

### Phase 3: Remaining Tool Migration

Migrate:

- 画布
- 工作流
- 任务中心
- 定时任务
- 评估中心
- Agent 指标
- 回收站

Acceptance:

- All sidebar tool destinations share the same shell structure.
- Tool-specific actions remain available.
- Empty/loading/error states are consistent.
- The sidebar no longer opens a mix of unrelated visual systems.

### Phase 4: Form and Dialog Unification

Migrate short forms to PaperDialog where appropriate.

Primary target:

- 创建日程

Possible later targets:

- Small workflow creation forms.
- Small task creation forms.
- Schedule edit forms.

Acceptance:

- The schedule form no longer uses old white/blue/orange styling.
- The dialog is visually consistent with the workbench.
- The form remains fast to open and does not require full-screen navigation.

## Interaction Rules

- Sidebar entries should open the corresponding tool directly.
- Back returns to the previous note/editor context.
- Refresh, export, filter, and close/back should use familiar icon buttons with accessible labels.
- Tool-specific primary actions should appear in the top bar or right detail panel, not scattered across the page.
- Selecting a graph node, timeline item, trace, task, or deleted note updates the right detail panel instead of opening unrelated popovers.
- Destructive actions such as permanent delete require confirmation.

## Data and State Boundaries

The redesign is UI-level only.

Important boundaries:

- Graph data remains graph data.
- Timeline events remain timeline events.
- Trace data remains trace data.
- RAG note content must not be shown as user profile memory unless explicitly provided by the relevant memory UI.
- Selected-note workflows must stay scoped to the current note where applicable.
- The workbench shell does not own business logic. It only standardizes layout, controls, and state presentation.

## Error, Loading, and Empty States

Every migrated tool should expose four states through shared patterns:

- Loading: quiet skeleton or spinner in the main content area.
- Empty: short explanation plus one relevant action.
- Error: readable error message, retry action, and optional details disclosure.
- Permission or unavailable: clear message without stack traces in the UI.

For trace and agent tooling, backend failures should remain observable and must not be silently swallowed.

## Testing Plan

Unit/component tests:

- ToolWorkbenchShell renders title, subtitle, actions, rail, main content, and detail content.
- PaperDialog renders title, close action, primary action, and form content.
- Migrated tools still render existing loaded, empty, and error states.

Integration or UI tests:

- Sidebar opens graph, timeline, and trace workbench views.
- Graph node selection updates the right detail panel.
- Timeline item selection updates the right detail panel.
- Trace selection updates the right detail panel.
- Schedule creation opens PaperDialog, not the old styled modal.
- Deleted note preview and restore/delete actions appear inside the recycle bin workbench.

Visual verification:

- Desktop width: top bar actions do not overlap.
- Narrow width: detail panel collapses or moves below content.
- Tool pages use warm paper palette and do not introduce blue/admin styling.

## Risks

- Several current tools own their own layout and CSS. Migrating them all at once would be risky.
- Inline styles in task and schedule views may require careful extraction.
- The graph view may need more responsive work than the list-based tools.
- If shared shell props become too generic, tool pages may become harder to read. Keep the shell focused on layout, not business logic.

## Open Decisions for Implementation Planning

1. Whether to build the shell as plain CSS modules/CSS files or fold it into the existing `features.css` style layer.
2. Whether to route all tools through one route state or keep current sidebar state and only swap components.
3. How aggressively to remove old CSS after each tool migration.

The implementation plan should resolve these after reading the current frontend routing/state code.

## Approval Notes

The approved scope is:

- Prioritize 关系图谱, 时间线, and 调用追踪.
- Include the other sidebar tools in the same migration plan.
- Keep 创建日程 as a lightweight unified dialog unless implementation review reveals a strong reason to promote it to a full-screen page.
