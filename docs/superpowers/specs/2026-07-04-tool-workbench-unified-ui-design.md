# Tool Workbench Unified UI Design

Date: 2026-07-04
Status: Approved, audited, and ready for implementation planning

## Background

The left sidebar currently groups several AI and productivity tools under "视图", "工具", and "其他", but their destinations are not one coherent UI system.

Current code facts from the audit:

- `frontend/src/App.tsx` opens most tools through individual Zustand booleans from `frontend/src/stores/uiStore.ts`.
- `GraphView`, `TimelineView`, and `TracesPanel` still use old overlay/drawer CSS with white/blue/gray styling and `z-index: 8888`.
- `CanvasView`, `WorkflowsView`, and `EvalDashView` already use `features.css`, but they each own their own page header and layout.
- `TaskPanelView` and `TaskScheduleView` are full-screen but rely heavily on inline styles.
- `AgentMetricsDashboard` has a warm paper palette but still reads like a separate admin page.
- `TrashView` is embedded inside `EditorPane` through `viewMode='trash'`, not opened as the same kind of tool overlay as the rest.
- `ScheduleForm` has accessible dialog behavior, but its CSS is still old white/blue/orange styling.

The approved design direction is to keep the existing product structure and tool capabilities, but unify the presentation through one shared "Tool Workbench" shell.

## Goals

1. Make all sidebar tool destinations feel like the same product area.
2. Prioritize implementation for:
   - 关系图谱
   - 时间线
   - 调用追踪
3. Put the remaining sidebar tools into the same migration path:
   - 画布
   - 创建日程
   - 工作流
   - 任务中心
   - 定时任务
   - 评估中心
   - Agent 指标
   - 回收站
4. Preserve existing backend APIs, data loading, and user-visible behavior unless the implementation plan calls out a UI-only state fix.
5. Use the existing warm paper token system and `features.css` conventions. Do not introduce a new design dependency.
6. Keep the AI 记忆 management entry in Settings as the canonical management surface; do not add a duplicate AI memory tool entry back into the editor toolbar.

## Non-Goals

- Do not redesign the note editor.
- Do not redesign backend memory, RAG, agent, workflow, evaluation, or schedule semantics.
- Do not replace `react-force-graph-2d`, `react-window`, or existing data APIs.
- Do not change authentication or routing architecture.
- Do not move every tool to URL routes in this pass.

## Executable Architecture Decision

Use a shared full-screen Tool Workbench shell, but keep the current `App.tsx` boolean-opened overlay model for the first implementation.

Reason:

- The current app already opens graph, timeline, trace, canvas, workflow, eval, task, schedule, metrics, and settings through Zustand flags.
- Replacing that with a route model would be a separate navigation refactor.
- A shared shell can be introduced safely while each tool keeps its current props and data flow.

The first implementation should create reusable layout primitives, then migrate tools one at a time.

## First-Priority Selection Policy

Graph, timeline, and trace migration intentionally changes the first click from "open immediately and close the panel" to "select item and show the right detail panel".

New behavior:

- Graph node click selects the node. The right panel exposes "打开笔记" or "编辑日程" when the node is actionable.
- Timeline row click selects the note or schedule. The right panel exposes "打开笔记" or "编辑日程".
- Trace row click selects the trace. The right panel shows call details, output, tools, and errors.
- Back/close still returns to the previous note/editor context.

Reason:

- The approved workbench design depends on a stable right detail panel.
- Opening immediately would make the detail panel unreachable for graph and timeline items.
- This is a UI state behavior change only. It does not change graph, timeline, schedule, note, or trace data APIs.

## Shared Components

### `ToolWorkbenchShell`

Create in:

- `frontend/src/components/workbench/ToolWorkbenchShell.tsx`
- `frontend/src/components/workbench/ToolWorkbenchShell.css`
- `frontend/src/components/workbench/index.ts`

Responsibilities:

- Render one full-screen fixed workbench layer.
- Render top bar, title, subtitle, status chips, primary actions, secondary actions, optional left rail, main slot, and optional right detail panel.
- Keep z-index consistent with existing full-screen tools.
- Close on Escape only when `closeOnEscape` is true.
- Keep focus and labels accessible enough for existing test standards.

Required prop shape:

```ts
interface ToolAction {
  key: string;
  label: string;
  icon?: React.ReactNode;
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  disabled?: boolean;
  onClick: () => void;
}

interface ToolWorkbenchShellProps {
  title: string;
  subtitle?: string;
  icon?: React.ReactNode;
  chips?: React.ReactNode;
  primaryActions?: ToolAction[];
  secondaryActions?: ToolAction[];
  leftRail?: React.ReactNode;
  detailPanel?: React.ReactNode;
  detailPanelLabel?: string;
  children: React.ReactNode;
  onBack: () => void;
  backLabel?: string;
  className?: string;
  mainClassName?: string;
  closeOnEscape?: boolean;
}
```

### `ToolControlRail`

Create in:

- `frontend/src/components/workbench/ToolControlRail.tsx`

Responsibilities:

- Render consistent rail sections for scope, search, filters, status, legends, and secondary operations.
- Avoid owning business logic.

Required prop shape:

```ts
interface ToolControlRailSection {
  key: string;
  title: string;
  content: React.ReactNode;
}

interface ToolControlRailProps {
  sections: ToolControlRailSection[];
  footer?: React.ReactNode;
}
```

### `ToolDetailPanel`

Create in:

- `frontend/src/components/workbench/ToolDetailPanel.tsx`

Responsibilities:

- Render a consistent right-side panel for selected graph nodes, timeline items, traces, task plans, or deleted notes.
- Provide one empty state for "no selection".
- Keep destructive actions visually separated.

Required prop shape:

```ts
interface ToolDetailPanelProps {
  title: string;
  subtitle?: string;
  meta?: React.ReactNode;
  actions?: React.ReactNode;
  dangerActions?: React.ReactNode;
  children?: React.ReactNode;
  empty?: boolean;
  emptyMessage?: string;
}
```

### `PaperDialog`

Do not build a second dialog framework unless implementation review proves it is needed. First attempt should restyle `ScheduleForm.css` to match the existing `.dialog-*` and `.lp-modal-*` warm paper conventions.

If reuse becomes awkward, create:

- `frontend/src/components/workbench/PaperDialog.tsx`

## Visual Rules

- Use existing tokens from `frontend/src/tailwind.css`: `--color-paper-*`, `--color-border`, `--color-text`, `--color-text-secondary`, `--color-accent`, `--color-sage`, and status colors.
- Default control radius: 8px.
- Top bar height: stable, about 64px to 72px.
- Left rail width: 232px to 260px.
- Right detail panel width: 280px to 340px.
- Main region must use `min-width: 0` and `min-height: 0` so canvas/list content does not overflow.
- Avoid nested cards inside cards.
- Remove old black modal backdrops from graph, timeline, and traces.
- Remove old blue focus/accent color from graph and timeline UI.
- Use Chinese visible labels.
- Use icon buttons for close/back, refresh, export, and filter actions where practical.

## Tool Mapping

| Sidebar Entry | Target Pattern | Implementation Notes |
| --- | --- | --- |
| 关系图谱 | Canvas workbench | First priority. Keep `ForceGraph2D`, remote graph fallback, local wiki-link fallback, and selected-note centering. Change node click to select-first, then open from right detail panel. Replace overlay/panel CSS with shell, rail legend, and right detail panel. |
| 时间线 | List-detail workbench | First priority. Keep `VariableSizeList`, date grouping, note opening, schedule editing, and schedule status updates. Change row click to select-first, then open/edit from right detail panel. Move from right drawer to full workbench. |
| 调用追踪 | Dashboard/list workbench | First priority. Keep `getAgentTraces`, `getTraceStats`, and `clearChatMemory`. Add error state instead of only `console.error`. Move selected trace detail to right panel. |
| 画布 | Canvas workbench | Second wave. Keep canvas list/editor modes and node editing logic. Wrap list and editor views in shell. |
| 创建日程 | Paper dialog | Restyle `ScheduleForm.css`. Keep existing focus trap, labels, Escape handling, save behavior, and tests. |
| 工作流 | Dashboard workbench | Second wave. Keep tabs, create modal, run polling, run detail modal. Wrap with shell and later replace local modals with PaperDialog-compatible styling. |
| 任务中心 | List-detail workbench | Second wave. Keep plan detail and `PlanApprovalCard`; remove local full-screen header styles. |
| 定时任务 | List-detail workbench | Second wave. Keep create/toggle/delete behavior; move inline form into shell content or PaperDialog based on implementation complexity. |
| 评估中心 | Dashboard workbench | Second wave. Keep dataset/runs/detail flow and polling; move existing stats/content into shell layout. |
| Agent 指标 | Dashboard workbench | Second wave. Keep metrics polling and cards/table; remove breadcrumb/admin framing and use shell top bar. |
| 回收站 | List-detail workbench | Second wave. First keep `viewMode='trash'` behavior working. Then open recycle bin through shell without breaking `EditorPane` tests. |

## Implementation Phases

### Phase 1: Shared Workbench Foundation

Files to create:

- `frontend/src/components/workbench/ToolWorkbenchShell.tsx`
- `frontend/src/components/workbench/ToolWorkbenchShell.css`
- `frontend/src/components/workbench/ToolControlRail.tsx`
- `frontend/src/components/workbench/ToolDetailPanel.tsx`
- `frontend/src/components/workbench/index.ts`
- `frontend/tests/tool-workbench.behavior.test.tsx`

Files to modify:

- `frontend/src/App.tsx` only if the shell requires a shared wrapper or import side effect.

Acceptance:

- Shell renders title, subtitle, back button, actions, left rail, main content, and detail panel.
- Escape calls `onBack` when `closeOnEscape` is true.
- Shell uses warm paper tokens and does not require any backend API.

### Phase 2: First-Priority Tool Migration

Files to modify:

- `frontend/src/components/GraphView.tsx`
- `frontend/src/components/GraphView.css`
- `frontend/src/components/TimelineView.tsx`
- `frontend/src/components/TimelineView.css`
- `frontend/src/components/TracesPanel.tsx`
- `frontend/src/components/TracesPanel.css`
- `frontend/tests/components-ui.behavior.test.tsx`
- `frontend/tests/frontendStability.test.ts`

Acceptance:

- Graph, timeline, and traces open as full-screen workbenches.
- Graph and timeline items use select-first interaction with explicit open/edit actions in the right panel.
- Trace loading failure is visible in the UI.
- Selected graph/timeline/trace details appear in the right detail panel.
- Old overlay classes are either removed or no longer provide black backdrop/old white panel styling.

### Phase 3: Existing Warm Tool Migration

Files to modify:

- `frontend/src/components/features/CanvasView.tsx`
- `frontend/src/components/features/WorkflowsView.tsx`
- `frontend/src/components/features/EvalDashView.tsx`
- `frontend/src/components/features/features.css`
- `frontend/src/components/admin/AgentMetricsDashboard.tsx`
- `frontend/src/components/admin/agent-metrics.css`

Acceptance:

- These tools use the same top bar and page frame.
- Tool-specific cards, tables, and modals remain functional.
- Agent Metrics no longer shows settings/admin breadcrumb as the main identity.

### Phase 4: Inline Style and Recycle Bin Migration

Files to modify:

- `frontend/src/components/features/TaskPanelView.tsx`
- `frontend/src/components/features/TaskScheduleView.tsx`
- `frontend/src/TrashView.tsx`
- `frontend/src/components/EditorPane.tsx`
- `frontend/src/hooks/useNotes.ts`
- `frontend/src/stores/uiStore.ts`
- `frontend/tests/workflow-dialogs-trash.behavior.test.tsx`
- `frontend/tests/stores.behavior.test.ts`

Acceptance:

- Task center and scheduled tasks use the shared shell.
- Recycle bin appears as a workbench tool while existing restore/delete tests still pass.
- Permanent delete still requires confirmation.

### Phase 5: Form Unification

Files to modify:

- `frontend/src/components/ScheduleForm.css`
- `frontend/src/components/ScheduleForm.tsx` only if class hooks or button labels need small adjustments.
- `frontend/tests/schedule-form.behavior.test.tsx`
- `frontend/tests/frontendStability.test.ts`

Acceptance:

- Schedule form matches the warm paper dialog style.
- Existing accessibility behavior remains intact.
- Existing save/create/edit tests pass.

## Test-First Requirements

For each phase:

1. Write or update the relevant tests first.
2. Run the focused frontend test file and verify the new/changed test fails for the intended reason.
3. Implement the minimum code to pass.
4. Run the focused test again.
5. Run `npm test`.
6. Run `npm run build`.
7. Run `npm run lint` before the final implementation commit.

Minimum required new/updated tests:

- `tool-workbench.behavior.test.tsx`: shell rendering and Escape close.
- `components-ui.behavior.test.tsx`: graph/timeline migration behavior.
- A trace test, either in `components-ui.behavior.test.tsx` or a new `traces-panel.behavior.test.tsx`.
- `schedule-form.behavior.test.tsx`: schedule dialog remains accessible after restyle.
- `workflow-dialogs-trash.behavior.test.tsx`: recycle bin behavior still works after migration.
- `frontendStability.test.ts`: old close labels/class assumptions updated to new workbench selectors.

## Rollback Plan

- The shared shell is additive in Phase 1.
- Each migrated tool keeps its existing public props.
- If one tool migration fails, revert that tool file and CSS while leaving the shell in place.
- Do not delete old CSS until the migrated component no longer imports or depends on it.

## Definition of Done

- All sidebar tools either use the shared workbench shell or have a documented later migration task in the implementation plan.
- First-priority tools are migrated in code.
- The schedule form no longer looks like the old white/blue/orange modal.
- No duplicate AI memory toolbar entry is reintroduced.
- `npm test`, `npm run build`, and `npm run lint` pass.
- Manual browser review confirms graph, timeline, trace, schedule form, and at least one second-wave tool match the unified warm paper style.
