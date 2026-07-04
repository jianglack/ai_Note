# Tool Workbench Unified UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a unified warm-paper full-screen workbench UI for the sidebar tools, prioritizing 关系图谱, 时间线, and 调用追踪 while keeping current APIs and app state boundaries.

**Architecture:** Add shared workbench layout primitives under `frontend/src/components/workbench`, keep the current `App.tsx` Zustand boolean overlay model, then migrate tools one at a time. First-priority tools move from old modal/drawer styles to select-first workbench pages with a right detail panel; later tools reuse the same shell with smaller behavior changes.

**Tech Stack:** React 18, TypeScript, Zustand, Vite, Vitest, Testing Library, `react-force-graph-2d`, `react-window`, existing CSS tokens from `frontend/src/tailwind.css` and `features.css`.

---

## Working Rules

- Do not modify backend code for this UI pass.
- Do not introduce routing changes; keep existing `uiStore` booleans unless a task explicitly changes recycle bin state.
- Do not stage existing unrelated untracked files under `docs/superpowers/plans` or `docs/superpowers/specs`.
- Use TDD for each task: write/update tests, run focused failing test, implement, rerun focused test, then run broader frontend checks.
- Use `apply_patch` for manual edits.
- Commit after each completed task.

## File Map

Create:

- `frontend/src/components/workbench/ToolWorkbenchShell.tsx` - full-screen shell and toolbar action rendering.
- `frontend/src/components/workbench/ToolWorkbenchShell.css` - shared warm-paper layout and responsive rules.
- `frontend/src/components/workbench/ToolControlRail.tsx` - reusable left rail section renderer.
- `frontend/src/components/workbench/ToolDetailPanel.tsx` - reusable right detail/empty/danger panel.
- `frontend/src/components/workbench/index.ts` - barrel exports.
- `frontend/tests/tool-workbench.behavior.test.tsx` - shell behavior tests.

Modify first priority:

- `frontend/src/components/GraphView.tsx`
- `frontend/src/components/GraphView.css`
- `frontend/src/components/TimelineView.tsx`
- `frontend/src/components/TimelineView.css`
- `frontend/src/components/TracesPanel.tsx`
- `frontend/src/components/TracesPanel.css`
- `frontend/tests/components-ui.behavior.test.tsx`
- `frontend/tests/frontendStability.test.ts`

Modify second wave:

- `frontend/src/components/features/CanvasView.tsx`
- `frontend/src/components/features/WorkflowsView.tsx`
- `frontend/src/components/features/EvalDashView.tsx`
- `frontend/src/components/features/features.css`
- `frontend/src/components/admin/AgentMetricsDashboard.tsx`
- `frontend/src/components/admin/agent-metrics.css`
- `frontend/src/components/features/TaskPanelView.tsx`
- `frontend/src/components/features/TaskScheduleView.tsx`
- `frontend/src/TrashView.tsx`
- `frontend/src/components/EditorPane.tsx`
- `frontend/src/hooks/useNotes.ts`
- `frontend/src/stores/uiStore.ts`
- `frontend/src/components/ScheduleForm.css`

## Task 1: Shared Workbench Shell

**Files:**

- Create: `frontend/src/components/workbench/ToolWorkbenchShell.tsx`
- Create: `frontend/src/components/workbench/ToolWorkbenchShell.css`
- Create: `frontend/src/components/workbench/ToolControlRail.tsx`
- Create: `frontend/src/components/workbench/ToolDetailPanel.tsx`
- Create: `frontend/src/components/workbench/index.ts`
- Test: `frontend/tests/tool-workbench.behavior.test.tsx`

- [ ] **Step 1: Write the failing shell test**

Create `frontend/tests/tool-workbench.behavior.test.tsx`:

```tsx
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import {
  ToolControlRail,
  ToolDetailPanel,
  ToolWorkbenchShell,
} from '../src/components/workbench';

describe('ToolWorkbenchShell', () => {
  it('renders warm workbench regions and actions', async () => {
    const user = userEvent.setup();
    const onBack = vi.fn();
    const onRefresh = vi.fn();
    const onPrimary = vi.fn();

    render(
      <ToolWorkbenchShell
        title="关系图谱"
        subtitle="当前空间 · 2 篇笔记"
        chips={<span>2 nodes</span>}
        onBack={onBack}
        backLabel="关闭关系图谱"
        secondaryActions={[{ key: 'refresh', label: '刷新', onClick: onRefresh }]}
        primaryActions={[{ key: 'focus', label: '聚焦当前笔记', variant: 'primary', onClick: onPrimary }]}
        leftRail={<ToolControlRail sections={[{ key: 'scope', title: '范围', content: <button type="button">当前空间</button> }]} />}
        detailPanel={<ToolDetailPanel title="节点详情">Alpha</ToolDetailPanel>}
      >
        <div>Graph canvas</div>
      </ToolWorkbenchShell>,
    );

    expect(screen.getByRole('dialog', { name: '关系图谱' })).toBeInTheDocument();
    expect(screen.getByText('当前空间 · 2 篇笔记')).toBeInTheDocument();
    expect(screen.getByText('范围')).toBeInTheDocument();
    expect(screen.getByText('Graph canvas')).toBeInTheDocument();
    expect(screen.getByRole('complementary', { name: '节点详情' })).toHaveTextContent('Alpha');

    await user.click(screen.getByRole('button', { name: '刷新' }));
    await user.click(screen.getByRole('button', { name: '聚焦当前笔记' }));
    await user.click(screen.getByRole('button', { name: '关闭关系图谱' }));

    expect(onRefresh).toHaveBeenCalledTimes(1);
    expect(onPrimary).toHaveBeenCalledTimes(1);
    expect(onBack).toHaveBeenCalledTimes(1);
  });

  it('closes on Escape when enabled', () => {
    const onBack = vi.fn();
    render(
      <ToolWorkbenchShell title="调用追踪" onBack={onBack} closeOnEscape>
        Trace body
      </ToolWorkbenchShell>,
    );

    fireEvent.keyDown(window, { key: 'Escape' });
    expect(onBack).toHaveBeenCalledTimes(1);
  });

  it('renders an empty detail panel state', () => {
    render(<ToolDetailPanel title="详情" empty emptyMessage="请选择一项" />);
    expect(screen.getByRole('complementary', { name: '详情' })).toHaveTextContent('请选择一项');
  });
});
```

- [ ] **Step 2: Run the focused test and verify failure**

Run:

```powershell
cd D:\ainotetest\frontend
npm test -- tests/tool-workbench.behavior.test.tsx
```

Expected: FAIL because `../src/components/workbench` does not exist.

- [ ] **Step 3: Implement the shared components**

Create `ToolWorkbenchShell.tsx` with these exported types and behavior:

```tsx
import { useEffect, type ReactNode } from 'react';
import './ToolWorkbenchShell.css';

export interface ToolAction {
  key: string;
  label: string;
  icon?: ReactNode;
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  disabled?: boolean;
  onClick: () => void;
}

interface ToolWorkbenchShellProps {
  title: string;
  subtitle?: string;
  icon?: ReactNode;
  chips?: ReactNode;
  primaryActions?: ToolAction[];
  secondaryActions?: ToolAction[];
  leftRail?: ReactNode;
  detailPanel?: ReactNode;
  detailPanelLabel?: string;
  children: ReactNode;
  onBack: () => void;
  backLabel?: string;
  className?: string;
  mainClassName?: string;
  closeOnEscape?: boolean;
}

function renderAction(action: ToolAction) {
  return (
    <button
      key={action.key}
      type="button"
      className={`twb-action twb-action-${action.variant || 'secondary'}`}
      disabled={action.disabled}
      onClick={action.onClick}
      aria-label={action.label}
    >
      {action.icon && <span className="twb-action-icon">{action.icon}</span>}
      <span>{action.label}</span>
    </button>
  );
}

export default function ToolWorkbenchShell({
  title,
  subtitle,
  icon,
  chips,
  primaryActions = [],
  secondaryActions = [],
  leftRail,
  detailPanel,
  children,
  onBack,
  backLabel,
  className,
  mainClassName,
  closeOnEscape = false,
}: ToolWorkbenchShellProps) {
  useEffect(() => {
    if (!closeOnEscape) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onBack();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [closeOnEscape, onBack]);

  return (
    <section className={`twb-shell ${className || ''}`} role="dialog" aria-modal="true" aria-label={title}>
      <header className="twb-topbar">
        <div className="twb-title-group">
          <button type="button" className="twb-back" aria-label={backLabel || `关闭${title}`} onClick={onBack}>‹</button>
          {icon && <span className="twb-title-icon">{icon}</span>}
          <div className="twb-title-copy">
            <h1>{title}</h1>
            {subtitle && <p>{subtitle}</p>}
          </div>
          {chips && <div className="twb-chips">{chips}</div>}
        </div>
        <div className="twb-actions">
          {secondaryActions.map(renderAction)}
          {primaryActions.map(renderAction)}
        </div>
      </header>
      <div className={`twb-body ${leftRail ? 'twb-has-rail' : ''} ${detailPanel ? 'twb-has-detail' : ''}`}>
        {leftRail && <aside className="twb-rail">{leftRail}</aside>}
        <main className={`twb-main ${mainClassName || ''}`}>{children}</main>
        {detailPanel && <aside className="twb-detail">{detailPanel}</aside>}
      </div>
    </section>
  );
}
```

Create `ToolControlRail.tsx`:

```tsx
import type { ReactNode } from 'react';

export interface ToolControlRailSection {
  key: string;
  title: string;
  content: ReactNode;
}

interface ToolControlRailProps {
  sections: ToolControlRailSection[];
  footer?: ReactNode;
}

export default function ToolControlRail({ sections, footer }: ToolControlRailProps) {
  return (
    <div className="twb-control-rail">
      {sections.map(section => (
        <section className="twb-rail-section" key={section.key}>
          <h2>{section.title}</h2>
          <div>{section.content}</div>
        </section>
      ))}
      {footer && <div className="twb-rail-footer">{footer}</div>}
    </div>
  );
}
```

Create `ToolDetailPanel.tsx`:

```tsx
import type { ReactNode } from 'react';

interface ToolDetailPanelProps {
  title: string;
  subtitle?: string;
  meta?: ReactNode;
  actions?: ReactNode;
  dangerActions?: ReactNode;
  children?: ReactNode;
  empty?: boolean;
  emptyMessage?: string;
}

export default function ToolDetailPanel({
  title,
  subtitle,
  meta,
  actions,
  dangerActions,
  children,
  empty = false,
  emptyMessage = '请选择一项查看详情',
}: ToolDetailPanelProps) {
  return (
    <section className="twb-detail-panel" role="complementary" aria-label={title}>
      <div className="twb-detail-head">
        <h2>{title}</h2>
        {subtitle && <p>{subtitle}</p>}
      </div>
      {meta && <div className="twb-detail-meta">{meta}</div>}
      <div className="twb-detail-content">
        {empty ? <div className="twb-empty">{emptyMessage}</div> : children}
      </div>
      {actions && <div className="twb-detail-actions">{actions}</div>}
      {dangerActions && <div className="twb-detail-danger">{dangerActions}</div>}
    </section>
  );
}
```

Create `index.ts`:

```ts
export { default as ToolWorkbenchShell } from './ToolWorkbenchShell';
export type { ToolAction } from './ToolWorkbenchShell';
export { default as ToolControlRail } from './ToolControlRail';
export type { ToolControlRailSection } from './ToolControlRail';
export { default as ToolDetailPanel } from './ToolDetailPanel';
```

Create CSS with these required selectors: `.twb-shell`, `.twb-topbar`, `.twb-body`, `.twb-rail`, `.twb-main`, `.twb-detail`, `.twb-action-primary`, `.twb-empty`. Use warm paper variables and responsive collapse below 980px.

- [ ] **Step 4: Run focused test**

Run:

```powershell
cd D:\ainotetest\frontend
npm test -- tests/tool-workbench.behavior.test.tsx
```

Expected: PASS.

- [ ] **Step 5: Run broader checks**

Run:

```powershell
cd D:\ainotetest\frontend
npm test
npm run build
```

Expected: both pass.

- [ ] **Step 6: Commit**

```powershell
cd D:\ainotetest
git add frontend/src/components/workbench frontend/tests/tool-workbench.behavior.test.tsx
git commit -m "Add shared tool workbench shell"
```

## Task 2: Migrate Graph View

**Files:**

- Modify: `frontend/src/components/GraphView.tsx`
- Modify: `frontend/src/components/GraphView.css`
- Modify: `frontend/tests/components-ui.behavior.test.tsx`
- Modify: `frontend/tests/frontendStability.test.ts`

- [ ] **Step 1: Update graph behavior test first**

In `components-ui.behavior.test.tsx`, replace the existing graph test expectation so clicking a node selects it and does not close immediately:

```tsx
it('GraphView selects a node before opening it from the detail panel', async () => {
  apiMocks.getKnowledgeGraph.mockRejectedValueOnce(new Error('graph offline'));
  const onSelectNote = vi.fn();
  const onClose = vi.fn();
  const alpha = makeNote();
  const beta = makeNote({ id: 'note-2', title: 'Beta', content: 'Target' });

  render(
    <GraphView
      notes={[alpha, beta]}
      schedules={[]}
      selectedNoteId={undefined}
      onSelectNote={onSelectNote}
      onSelectSchedule={vi.fn()}
      onClose={onClose}
    />,
  );

  await waitFor(() => {
    expect(screen.getByText(/2 nodes/)).toHaveTextContent('1 links');
  });

  await userEvent.click(screen.getByRole('button', { name: 'Beta' }));

  expect(onSelectNote).not.toHaveBeenCalled();
  expect(onClose).not.toHaveBeenCalled();
  expect(screen.getByRole('complementary', { name: '节点详情' })).toHaveTextContent('Beta');

  await userEvent.click(screen.getByRole('button', { name: '打开笔记' }));

  expect(onSelectNote).toHaveBeenCalledWith(beta);
  expect(onClose).toHaveBeenCalled();
});
```

Update `frontendStability.test.ts` close-label guard to accept the new back label:

```ts
['src/components/GraphView.tsx', /backLabel="关闭关系图谱"/],
```

- [ ] **Step 2: Run focused failing tests**

```powershell
cd D:\ainotetest\frontend
npm test -- tests/components-ui.behavior.test.tsx tests/frontendStability.test.ts
```

Expected: FAIL because GraphView still opens and closes on node click.

- [ ] **Step 3: Implement GraphView migration**

Use these imports:

```tsx
import {
  ToolControlRail,
  ToolDetailPanel,
  ToolWorkbenchShell,
} from './workbench';
```

Add state:

```tsx
const [selectedNode, setSelectedNode] = useState<GraphNode | null>(null);
```

Change `handleNodeClick` to select:

```tsx
const handleNodeClick = useCallback((node: GraphNode) => {
  setSelectedNode(node);
}, []);
```

Add open action:

```tsx
const openSelectedNode = useCallback(() => {
  if (!selectedNode) return;
  if (selectedNode.type === 'schedule' && selectedNode.scheduleRef) {
    onSelectSchedule(selectedNode.scheduleRef);
    onClose();
    return;
  }
  if (selectedNode.noteRef) {
    onSelectNote(selectedNode.noteRef);
    onClose();
  }
}, [onClose, onSelectNote, onSelectSchedule, selectedNode]);
```

Render `ToolWorkbenchShell` instead of the old header/panel overlay. Keep `ForceGraph2D`, but set `backgroundColor="rgba(0,0,0,0)"`, use warm colors in `paintNode`, and move legend into `ToolControlRail`.

Right detail panel:

```tsx
<ToolDetailPanel
  title="节点详情"
  subtitle={selectedNode?.type === 'schedule' ? '日程' : selectedNode?.type === 'note' ? '笔记' : selectedNode?.type}
  empty={!selectedNode}
  emptyMessage="选择一个节点查看关系和来源。"
  actions={selectedNode?.noteRef || selectedNode?.scheduleRef ? (
    <button type="button" className="twb-action twb-action-primary" onClick={openSelectedNode}>
      {selectedNode.type === 'schedule' ? '编辑日程' : '打开笔记'}
    </button>
  ) : undefined}
>
  <div className="graph-detail-title">{selectedNode?.label}</div>
  <div className="graph-detail-meta">ID: {selectedNode?.refId || selectedNode?.id}</div>
</ToolDetailPanel>
```

- [ ] **Step 4: Replace graph CSS with workbench-compatible CSS**

`GraphView.css` should no longer define black modal backdrop styles. Keep only graph-specific selectors, for example:

```css
.graph-canvas {
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  background:
    linear-gradient(rgba(74,64,50,0.045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(74,64,50,0.045) 1px, transparent 1px),
    var(--color-paper-0, #fbf7ee);
  background-size: 28px 28px;
}

.graph-legend-list {
  display: grid;
  gap: 8px;
  font-size: 12px;
  color: var(--color-text-secondary, #7a6b58);
}
```

- [ ] **Step 5: Run focused and broad checks**

```powershell
cd D:\ainotetest\frontend
npm test -- tests/components-ui.behavior.test.tsx tests/frontendStability.test.ts
npm test
npm run build
```

Expected: all pass.

- [ ] **Step 6: Commit**

```powershell
cd D:\ainotetest
git add frontend/src/components/GraphView.tsx frontend/src/components/GraphView.css frontend/tests/components-ui.behavior.test.tsx frontend/tests/frontendStability.test.ts
git commit -m "Migrate graph view to tool workbench"
```

## Task 3: Migrate Timeline View

**Files:**

- Modify: `frontend/src/components/TimelineView.tsx`
- Modify: `frontend/src/components/TimelineView.css`
- Modify: `frontend/tests/components-ui.behavior.test.tsx`
- Modify: `frontend/tests/frontendStability.test.ts`

- [ ] **Step 1: Update timeline test first**

Replace the timeline test with select-first behavior:

```tsx
it('TimelineView selects an item before opening it from the detail panel', async () => {
  const user = userEvent.setup();
  const onSelectNote = vi.fn();
  const onClose = vi.fn();
  const alpha = makeNote();

  render(
    <TimelineView
      notes={[alpha]}
      schedules={[]}
      onSelectNote={onSelectNote}
      onSelectSchedule={vi.fn()}
      onScheduleStatusChange={vi.fn()}
      onClose={onClose}
    />,
  );

  await user.click(screen.getByRole('button', { name: 'Select timeline note Alpha' }));

  expect(onSelectNote).not.toHaveBeenCalled();
  expect(onClose).not.toHaveBeenCalled();
  expect(screen.getByRole('complementary', { name: '时间线详情' })).toHaveTextContent('Alpha');

  await user.click(screen.getByRole('button', { name: '打开笔记' }));

  expect(onSelectNote).toHaveBeenCalledWith(alpha);
  expect(onClose).toHaveBeenCalled();
});
```

Update stability guard:

```ts
['src/components/TimelineView.tsx', /backLabel="关闭时间线"/],
```

- [ ] **Step 2: Run focused failing tests**

```powershell
cd D:\ainotetest\frontend
npm test -- tests/components-ui.behavior.test.tsx tests/frontendStability.test.ts
```

Expected: FAIL because rows still open immediately.

- [ ] **Step 3: Implement timeline selection state**

Add:

```tsx
const [selectedItem, setSelectedItem] = useState<TimelineItem | null>(null);
```

Change note button aria label and click:

```tsx
aria-label={`Select timeline note ${item.data.title || 'Untitled'}`}
onClick={() => setSelectedItem(item)}
```

For schedule rows, wrap/select consistently if `ScheduleCard` cannot be made select-first directly. If `ScheduleCard` opens immediately, replace it inside timeline with a small timeline schedule button that calls `setSelectedItem(item)` and puts edit/status actions in the detail panel.

Add explicit open action:

```tsx
const openSelectedItem = () => {
  if (!selectedItem) return;
  if (selectedItem.type === 'note') {
    onSelectNote(selectedItem.data);
    onClose();
  } else {
    onSelectSchedule(selectedItem.data);
  }
};
```

Use `ToolWorkbenchShell` with:

- title `时间线`
- subtitle `${notes.length} 篇笔记 · ${schedules.length} 个日程`
- left rail sections for type counts
- main content containing the virtual list
- detail panel labeled `时间线详情`

- [ ] **Step 4: Replace timeline CSS**

Remove drawer/backdrop layout from `TimelineView.css`. Keep list styles only:

```css
.timeline-body {
  height: 100%;
  min-height: 0;
  overflow: hidden;
  padding: 18px 24px;
}

.timeline-note {
  background: var(--color-paper-0, #fbf7ee);
  border: 1px solid var(--color-border, rgba(74,64,50,0.12));
  color: var(--color-text, #2b2620);
}
```

- [ ] **Step 5: Run checks**

```powershell
cd D:\ainotetest\frontend
npm test -- tests/components-ui.behavior.test.tsx tests/frontendStability.test.ts
npm test
npm run build
```

Expected: all pass.

- [ ] **Step 6: Commit**

```powershell
cd D:\ainotetest
git add frontend/src/components/TimelineView.tsx frontend/src/components/TimelineView.css frontend/tests/components-ui.behavior.test.tsx frontend/tests/frontendStability.test.ts
git commit -m "Migrate timeline view to tool workbench"
```

## Task 4: Migrate Traces Panel

**Files:**

- Modify: `frontend/src/components/TracesPanel.tsx`
- Modify: `frontend/src/components/TracesPanel.css`
- Modify: `frontend/tests/components-ui.behavior.test.tsx`
- Modify: `frontend/tests/frontendStability.test.ts`

- [ ] **Step 1: Add trace tests first**

Extend `apiMocks` in `components-ui.behavior.test.tsx`:

```tsx
getAgentTraces: vi.fn(),
getTraceStats: vi.fn(),
clearChatMemory: vi.fn(),
```

Export mocks from `vi.mock('../src/api', ...)`.

Import `TracesPanel`.

Add:

```tsx
it('TracesPanel shows selected trace details in the workbench detail panel', async () => {
  apiMocks.getAgentTraces.mockResolvedValueOnce([{
    id: 'trace-1',
    inputText: 'Summarize note',
    outputText: 'Done',
    status: 'OK',
    model: 'test-model',
    inputTokens: 10,
    outputTokens: 20,
    totalTokens: 30,
    latencyMs: 150,
    toolsCalled: JSON.stringify([{ name: 'searchNotes' }]),
    errorMessage: null,
    createdAt: '2026-07-04T10:00:00Z',
  }]);
  apiMocks.getTraceStats.mockResolvedValueOnce({
    todayCalls: 1,
    todayTokens: 30,
    totalCalls: 1,
    totalTokens: 30,
  });

  render(<TracesPanel onClose={vi.fn()} />);

  await screen.findByText('Summarize note');
  await userEvent.click(screen.getByRole('button', { name: /Select trace/ }));

  expect(screen.getByRole('complementary', { name: '调用详情' })).toHaveTextContent('test-model');
  expect(screen.getByRole('complementary', { name: '调用详情' })).toHaveTextContent('searchNotes');
});

it('TracesPanel surfaces load failures', async () => {
  apiMocks.getAgentTraces.mockRejectedValueOnce(new Error('trace api down'));
  apiMocks.getTraceStats.mockResolvedValueOnce({
    todayCalls: 0,
    todayTokens: 0,
    totalCalls: 0,
    totalTokens: 0,
  });

  render(<TracesPanel onClose={vi.fn()} />);

  expect(await screen.findByRole('alert')).toHaveTextContent('trace api down');
});
```

Update stability guard:

```ts
['src/components/TracesPanel.tsx', /backLabel="关闭调用追踪"/],
```

- [ ] **Step 2: Run focused failing tests**

```powershell
cd D:\ainotetest\frontend
npm test -- tests/components-ui.behavior.test.tsx tests/frontendStability.test.ts
```

Expected: FAIL because traces still use old modal and inline expanded details.

- [ ] **Step 3: Implement trace workbench**

Add `error` state:

```tsx
const [error, setError] = useState<string | null>(null);
```

In `loadData`, set error on catch:

```tsx
} catch (err) {
  const message = err instanceof Error ? err.message : '调用追踪加载失败';
  setError(message);
} finally {
```

Replace outer overlay with `ToolWorkbenchShell`. Keep the clear memory button, refresh button, stats, and list. Change trace row label to:

```tsx
aria-label={`Select trace ${trace.inputText || trace.id}`}
```

Move trace detail markup into `ToolDetailPanel title="调用详情"`.

- [ ] **Step 4: Replace trace CSS**

Keep status, stats, and row styles but remove `.traces-overlay` black backdrop and `.traces-panel` modal dimensions. Add styles for `.traces-workbench-main`, `.traces-stats`, `.traces-list`, `.trace-item`, `.trace-selected`.

- [ ] **Step 5: Run checks**

```powershell
cd D:\ainotetest\frontend
npm test -- tests/components-ui.behavior.test.tsx tests/frontendStability.test.ts
npm test
npm run build
```

Expected: all pass.

- [ ] **Step 6: Commit**

```powershell
cd D:\ainotetest
git add frontend/src/components/TracesPanel.tsx frontend/src/components/TracesPanel.css frontend/tests/components-ui.behavior.test.tsx frontend/tests/frontendStability.test.ts
git commit -m "Migrate traces panel to tool workbench"
```

## Task 5: Migrate Canvas, Workflows, and Eval Dashboard

**Files:**

- Modify: `frontend/src/components/features/CanvasView.tsx`
- Modify: `frontend/src/components/features/WorkflowsView.tsx`
- Modify: `frontend/src/components/features/EvalDashView.tsx`
- Modify: `frontend/src/components/features/features.css`
- Modify: `frontend/tests/sidebar-canvas.behavior.test.tsx`
- Modify: `frontend/tests/components-ui.behavior.test.tsx`
- Modify: `frontend/tests/frontendStability.test.ts`

- [ ] **Step 1: Add/adjust tests first**

For Canvas in `sidebar-canvas.behavior.test.tsx`, assert the shell title and create button:

```tsx
expect(screen.getByRole('dialog', { name: '画布' })).toBeInTheDocument();
expect(screen.getByRole('button', { name: /新建画布/ })).toBeInTheDocument();
```

For Workflows in `components-ui.behavior.test.tsx`, keep Escape modal tests and also assert:

```tsx
expect(screen.getByRole('dialog', { name: 'AI 工作流' })).toBeInTheDocument();
```

For Eval dashboard, add a smoke render test with mocked dataset APIs if none exists:

```tsx
expect(screen.getByRole('dialog', { name: '评估中心' })).toBeInTheDocument();
```

- [ ] **Step 2: Run focused failing tests**

```powershell
cd D:\ainotetest\frontend
npm test -- tests/sidebar-canvas.behavior.test.tsx tests/components-ui.behavior.test.tsx tests/frontendStability.test.ts
```

Expected: FAIL where shell roles do not exist.

- [ ] **Step 3: Wrap CanvasView with ToolWorkbenchShell**

Keep existing list/editor modes. Replace local `wf-root` header in list mode and `cv-top` page frame in editor mode with shell top bar. Use the left rail for canvas list or node list when practical; if that is too broad, keep node list as the existing right panel but inside shell main/detail slots.

- [ ] **Step 4: Wrap WorkflowsView with ToolWorkbenchShell**

Use title `AI 工作流`, subtitle `定义多步 AI 任务，自动依次执行 · 共 ${flows.length} 个工作流`, primary action `创建工作流`, and keep tabs/main cards.

- [ ] **Step 5: Wrap EvalDashView with ToolWorkbenchShell**

Use title `评估中心`, primary action `开始评估`, secondary action `新建数据集`, left rail for datasets, main content for stats/items/history.

- [ ] **Step 6: Run checks and commit**

```powershell
cd D:\ainotetest\frontend
npm test -- tests/sidebar-canvas.behavior.test.tsx tests/components-ui.behavior.test.tsx tests/frontendStability.test.ts
npm test
npm run build
cd D:\ainotetest
git add frontend/src/components/features/CanvasView.tsx frontend/src/components/features/WorkflowsView.tsx frontend/src/components/features/EvalDashView.tsx frontend/src/components/features/features.css frontend/tests/sidebar-canvas.behavior.test.tsx frontend/tests/components-ui.behavior.test.tsx frontend/tests/frontendStability.test.ts
git commit -m "Migrate feature dashboards to tool workbench"
```

## Task 6: Migrate Agent Metrics

**Files:**

- Modify: `frontend/src/components/admin/AgentMetricsDashboard.tsx`
- Modify: `frontend/src/components/admin/agent-metrics.css`
- Modify: `frontend/tests/frontendStability.test.ts`

- [ ] **Step 1: Update tests first**

In `frontendStability.test.ts`, replace the close label check for agent metrics:

```ts
['src/components/admin/AgentMetricsDashboard.tsx', /backLabel="关闭 Agent 指标"/],
```

Add a guard that breadcrumb admin framing is removed:

```ts
it('Agent metrics uses the tool workbench shell instead of admin breadcrumbs', () => {
  const metrics = read('src/components/admin/AgentMetricsDashboard.tsx');
  assert.match(metrics, /ToolWorkbenchShell/);
  assert.doesNotMatch(metrics, /amd-breadcrumb/);
});
```

- [ ] **Step 2: Run focused failing test**

```powershell
cd D:\ainotetest\frontend
npm test -- tests/frontendStability.test.ts
```

Expected: FAIL because `AgentMetricsDashboard` still uses admin breadcrumb framing.

- [ ] **Step 3: Implement**

Wrap content with `ToolWorkbenchShell title="Agent 指标"`, subtitle showing last sampled time, secondary action refresh. Remove breadcrumb JSX. Keep metrics cards/table/detail sections unchanged inside main content.

- [ ] **Step 4: Run checks and commit**

```powershell
cd D:\ainotetest\frontend
npm test -- tests/frontendStability.test.ts
npm test
npm run build
cd D:\ainotetest
git add frontend/src/components/admin/AgentMetricsDashboard.tsx frontend/src/components/admin/agent-metrics.css frontend/tests/frontendStability.test.ts
git commit -m "Migrate agent metrics to tool workbench"
```

## Task 7: Migrate Task Center and Scheduled Tasks

**Files:**

- Modify: `frontend/src/components/features/TaskPanelView.tsx`
- Modify: `frontend/src/components/features/TaskScheduleView.tsx`
- Modify: `frontend/tests/frontendStability.test.ts`

- [ ] **Step 1: Update stability tests first**

Use new close labels:

```ts
['src/components/features/TaskPanelView.tsx', /backLabel="关闭任务中心"/],
['src/components/features/TaskScheduleView.tsx', /backLabel="关闭定时任务"/],
```

Add guards:

```ts
assert.match(read('src/components/features/TaskPanelView.tsx'), /ToolWorkbenchShell/);
assert.match(read('src/components/features/TaskScheduleView.tsx'), /ToolWorkbenchShell/);
```

- [ ] **Step 2: Run focused failing test**

```powershell
cd D:\ainotetest\frontend
npm test -- tests/frontendStability.test.ts
```

Expected: FAIL because both still use inline full-screen containers.

- [ ] **Step 3: Implement TaskPanelView shell**

Wrap the existing plan list and detail layout with `ToolWorkbenchShell title="任务中心"`, secondary refresh action, left rail containing plan list, main area containing selected `PlanApprovalCard`.

- [ ] **Step 4: Implement TaskScheduleView shell**

Wrap scheduled task list with `ToolWorkbenchShell title="定时任务"`, primary action toggling create form, main area containing form/list. Keep create/toggle/delete API behavior.

- [ ] **Step 5: Run checks and commit**

```powershell
cd D:\ainotetest\frontend
npm test -- tests/frontendStability.test.ts
npm test
npm run build
cd D:\ainotetest
git add frontend/src/components/features/TaskPanelView.tsx frontend/src/components/features/TaskScheduleView.tsx frontend/tests/frontendStability.test.ts
git commit -m "Migrate task tools to workbench shell"
```

## Task 8: Migrate Recycle Bin

**Files:**

- Modify: `frontend/src/TrashView.tsx`
- Modify: `frontend/src/components/EditorPane.tsx`
- Modify: `frontend/src/hooks/useNotes.ts`
- Modify: `frontend/src/stores/uiStore.ts`
- Modify: `frontend/tests/workflow-dialogs-trash.behavior.test.tsx`
- Modify: `frontend/tests/stores.behavior.test.ts`
- Modify: `frontend/tests/frontendStability.test.ts`

- [ ] **Step 1: Update tests first**

In `workflow-dialogs-trash.behavior.test.tsx`, update the close assertion:

```tsx
expect(screen.getByRole('dialog', { name: '回收站' })).toBeInTheDocument();
await user.click(screen.getByRole('button', { name: '关闭回收站' }));
expect(onClose).toHaveBeenCalled();
```

Add select-first behavior:

```tsx
await user.click(screen.getByRole('button', { name: '选择回收站笔记 Alpha' }));
expect(screen.getByRole('complementary', { name: '笔记预览' })).toHaveTextContent('Preview body');
```

- [ ] **Step 2: Run focused failing tests**

```powershell
cd D:\ainotetest\frontend
npm test -- tests/workflow-dialogs-trash.behavior.test.tsx tests/stores.behavior.test.ts tests/frontendStability.test.ts
```

Expected: FAIL because TrashView is not using shell/select-first detail panel.

- [ ] **Step 3: Implement TrashView shell**

Use `ToolWorkbenchShell title="回收站"` and keep props unchanged. Add `selectedNoteId` state. The main list uses buttons labeled `选择回收站笔记 ${title}`. The right `ToolDetailPanel title="笔记预览"` renders markdown preview and restore/permanent delete actions for selected note.

- [ ] **Step 4: Decide app integration minimally**

Keep `viewMode='trash'` if changing `EditorPane`/`useNotes` is unnecessary for tests. If the UI still embeds TrashView inside the editor after shell migration, allow it as an intermediate state only if the shell is fixed-position. Do not add a route model.

- [ ] **Step 5: Run checks and commit**

```powershell
cd D:\ainotetest\frontend
npm test -- tests/workflow-dialogs-trash.behavior.test.tsx tests/stores.behavior.test.ts tests/frontendStability.test.ts
npm test
npm run build
cd D:\ainotetest
git add frontend/src/TrashView.tsx frontend/src/components/EditorPane.tsx frontend/src/hooks/useNotes.ts frontend/src/stores/uiStore.ts frontend/tests/workflow-dialogs-trash.behavior.test.tsx frontend/tests/stores.behavior.test.ts frontend/tests/frontendStability.test.ts
git commit -m "Migrate recycle bin to tool workbench"
```

If `EditorPane.tsx`, `useNotes.ts`, or `uiStore.ts` are not changed, do not stage them.

## Task 9: Restyle Schedule Form

**Files:**

- Modify: `frontend/src/components/ScheduleForm.css`
- Modify: `frontend/src/components/ScheduleForm.tsx` only if class hooks are needed.
- Modify: `frontend/tests/schedule-form.behavior.test.tsx`
- Modify: `frontend/tests/frontendStability.test.ts`

- [ ] **Step 1: Add style/semantics guards first**

In `schedule-form.behavior.test.tsx`, add:

```tsx
it('uses the warm paper schedule dialog surface', () => {
  const { container } = render(
    <ScheduleForm notes={[]} onSave={vi.fn()} onClose={vi.fn()} />,
  );

  expect(container.querySelector('.schedule-form-overlay')).toBeInTheDocument();
  expect(container.querySelector('.schedule-form-panel')).toHaveClass('paper-texture');
});
```

If adding `paper-texture` class to the panel feels too invasive, use a class `schedule-form-paper` and assert that instead.

- [ ] **Step 2: Run focused failing tests**

```powershell
cd D:\ainotetest\frontend
npm test -- tests/schedule-form.behavior.test.tsx tests/frontendStability.test.ts
```

Expected: FAIL because the form still has old styling class only.

- [ ] **Step 3: Restyle CSS**

Keep the current component behavior. Change CSS to warm paper:

- overlay background `rgba(43, 38, 32, 0.32)`
- panel background `var(--color-paper-0)`
- border `1px solid var(--color-border)`
- buttons use `var(--color-accent)` and `var(--color-paper-2)`
- focus ring uses accent, not blue
- checkbox accent uses `var(--color-accent)`

If the test expects `paper-texture`, add:

```tsx
className="schedule-form-panel paper-texture"
```

- [ ] **Step 4: Run checks and commit**

```powershell
cd D:\ainotetest\frontend
npm test -- tests/schedule-form.behavior.test.tsx tests/frontendStability.test.ts
npm test
npm run build
cd D:\ainotetest
git add frontend/src/components/ScheduleForm.css frontend/src/components/ScheduleForm.tsx frontend/tests/schedule-form.behavior.test.tsx frontend/tests/frontendStability.test.ts
git commit -m "Restyle schedule form as paper dialog"
```

If `ScheduleForm.tsx` is not changed, do not stage it.

## Task 10: Full Frontend Verification and Browser Review

**Files:**

- Modify only files needed to fix issues found by verification.

- [ ] **Step 1: Run full frontend verification**

```powershell
cd D:\ainotetest\frontend
npm test
npm run build
npm run lint
```

Expected: all pass.

- [ ] **Step 2: Start or reuse dev server**

```powershell
cd D:\ainotetest\frontend
npm run dev -- --host 127.0.0.1
```

If the port is occupied, use the next available Vite URL shown in terminal.

- [ ] **Step 3: Manual browser checks**

Open the app and verify:

- 关系图谱 opens full-screen workbench and selecting a node updates the right panel.
- 时间线 opens full-screen workbench and selecting a row updates the right panel.
- 调用追踪 opens full-screen workbench and trace errors are visible.
- 创建日程 opens the warm paper dialog.
- At least one second-wave tool opens in the shared shell.
- Top bar actions do not overlap at desktop width.
- There is no duplicate AI 记忆 toolbar entry.

- [ ] **Step 4: Fix any verification defects**

For each defect, add or adjust a focused test first, then fix code. Rerun the focused test and full verification.

- [ ] **Step 5: Commit verification fixes**

```powershell
cd D:\ainotetest
git status --short
git add frontend/src/components/workbench/ToolWorkbenchShell.css
git commit -m "Polish unified tool workbench"
```

The `git add` line above is an example for a shell CSS fix. If verification fixes changed different files, replace that line with exact file paths from `git status --short`; do not use `git add .`. Skip this commit if there are no fixes.

## Task 11: Final Cleanup

**Files:**

- Modify CSS files only if unused old selectors remain after migration.

- [ ] **Step 1: Search for old overlay styles**

```powershell
cd D:\ainotetest
rg -n "background: rgba\\(0, 0, 0|z-index: 8888|Knowledge Graph|Close graph view|Close timeline|Close traces panel|#3b82f6|#4a90e2" frontend/src
```

Expected: no old graph/timeline/traces modal styling remains. If matches remain in unrelated legacy editor styles, document why they are unrelated before changing.

- [ ] **Step 2: Run final status**

```powershell
cd D:\ainotetest
git status --short
git log --oneline -8
```

Expected: only unrelated pre-existing untracked files remain, such as `.superpowers/` and older docs that were not part of this work.

- [ ] **Step 3: Final verification command**

```powershell
cd D:\ainotetest\frontend
npm test
npm run build
npm run lint
```

Expected: all pass.

- [ ] **Step 4: Final report**

Report:

- Files changed by phase.
- Tests passed.
- Any remaining risks.
- Whether browser review passed.
