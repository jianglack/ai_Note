import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { CSSProperties } from 'react';

const apiMocks = vi.hoisted(() => ({
  getKnowledgeGraph: vi.fn(),
  getWorkflows: vi.fn(),
  createWorkflow: vi.fn(),
  deleteWorkflow: vi.fn(),
  deleteMemory: vi.fn(),
  exportMemories: vi.fn(),
  getMemories: vi.fn(),
  toggleWorkflow: vi.fn(),
  runWorkflow: vi.fn(),
  getWorkflowRuns: vi.fn(),
  updateMemory: vi.fn(),
}));

const dialogMocks = vi.hoisted(() => ({
  askConfirm: vi.fn(),
}));

vi.mock('../src/api', () => ({
  getKnowledgeGraph: apiMocks.getKnowledgeGraph,
  getWorkflows: apiMocks.getWorkflows,
  createWorkflow: apiMocks.createWorkflow,
  deleteWorkflow: apiMocks.deleteWorkflow,
  deleteMemory: apiMocks.deleteMemory,
  exportMemories: apiMocks.exportMemories,
  getMemories: apiMocks.getMemories,
  toggleWorkflow: apiMocks.toggleWorkflow,
  runWorkflow: apiMocks.runWorkflow,
  getWorkflowRuns: apiMocks.getWorkflowRuns,
  updateMemory: apiMocks.updateMemory,
}));

vi.mock('../src/services/dialogService', () => ({
  askConfirm: dialogMocks.askConfirm,
}));

vi.mock('react-window', async () => {
  const React = await vi.importActual<typeof import('react')>('react');

  function renderRows(props: any) {
    return Array.from({ length: props.itemCount }).map((_, index) => {
      const rowHeight = typeof props.itemSize === 'function' ? props.itemSize(index) : props.itemSize;
      return props.children({
        index,
        style: { height: rowHeight } as CSSProperties,
        data: props.itemData,
      });
    });
  }

  const VariableSizeList = React.forwardRef((props: any, ref) => {
    React.useImperativeHandle(ref, () => ({ resetAfterIndex: vi.fn() }));
    return <div className={props.className}>{renderRows(props)}</div>;
  });

  const FixedSizeList = (props: any) => (
    <div className={props.className}>{renderRows(props)}</div>
  );

  return { VariableSizeList, FixedSizeList };
});

vi.mock('react-force-graph-2d', async () => {
  const React = await vi.importActual<typeof import('react')>('react');
  return {
    default: React.forwardRef((props: any, ref) => {
      React.useImperativeHandle(ref, () => ({
        centerAt: vi.fn(),
        zoom: vi.fn(),
      }));
      return (
        <div data-testid="force-graph">
          {props.graphData.nodes.map((node: any) => (
            <button key={node.id} type="button" onClick={() => props.onNodeClick(node)}>
              {node.label}
            </button>
          ))}
        </div>
      );
    }),
  };
});

vi.mock('reactflow', async () => {
  const React = await vi.importActual<typeof import('react')>('react');

  const ReactFlow = (props: any) => (
    <div data-testid="react-flow">
      {props.nodes.map((node: any) => (
        <button key={node.id} type="button">
          {node.data.label}
        </button>
      ))}
      {props.children}
    </div>
  );

  return {
    default: ReactFlow,
    Controls: () => <div data-testid="flow-controls" />,
    MiniMap: () => <div data-testid="flow-minimap" />,
    Background: () => <div data-testid="flow-background" />,
    Handle: () => <span data-testid="flow-handle" />,
    Position: { Left: 'left', Right: 'right' },
    BackgroundVariant: { Dots: 'dots' },
    useNodesState: (initial: any[]) => {
      const [state, setState] = React.useState(initial);
      return [state, setState, vi.fn()];
    },
    useEdgesState: (initial: any[]) => {
      const [state, setState] = React.useState(initial);
      return [state, setState, vi.fn()];
    },
    addEdge: (edge: any, edges: any[]) => [...edges, edge],
  };
});

import GraphView from '../src/components/GraphView';
import MindMapCanvas, { branchesToFlowData } from '../src/components/MindMapCanvas';
import SettingsPage from '../src/components/SettingsPage';
import TimelineView from '../src/components/TimelineView';
import WorkflowsView from '../src/components/features/WorkflowsView';
import { useToastStore } from '../src/stores/toastStore';
import { useUiStore } from '../src/stores/uiStore';
import type { Note, Schedule } from '../src/api';

function makeNote(overrides: Partial<Note> = {}): Note {
  return {
    id: 'note-1',
    title: 'Alpha',
    content: 'Alpha links [[Beta]]',
    folderId: null,
    createdAt: '2026-06-26T10:00:00Z',
    updatedAt: '2026-06-26T10:15:00Z',
    tags: [],
    ...overrides,
  };
}

function makeSchedule(overrides: Partial<Schedule> = {}): Schedule {
  return {
    id: 'schedule-1',
    title: 'Planning',
    startTime: '2026-06-26T12:00:00Z',
    allDay: false,
    status: 'pending',
    notes: [],
    createdAt: '2026-06-26T09:00:00Z',
    updatedAt: '2026-06-26T09:00:00Z',
    ...overrides,
  };
}

describe('core component behavior', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
    useToastStore.setState({ toasts: [] });
    useUiStore.setState({ isSettingsOpen: true });
    globalThis.ResizeObserver = class ResizeObserver {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(() => ({
      width: 640,
      height: 420,
      top: 0,
      left: 0,
      right: 640,
      bottom: 420,
      x: 0,
      y: 0,
      toJSON: () => {},
    } as DOMRect));
  });

  it('SettingsPage disables unavailable toggles and persists local settings', async () => {
    const user = userEvent.setup();
    const { container } = render(<SettingsPage />);

    expect(container.querySelectorAll('.settings-toggle:disabled')).toHaveLength(2);

    await user.click(container.querySelectorAll('.model-card')[0]);
    await user.click(container.querySelector('.settings-primary-btn')!);

    expect(JSON.parse(localStorage.getItem('ainote.ai-settings.v1')!).model).toBe('fast');
    expect(useToastStore.getState().toasts[0].type).toBe('success');

    await user.click(container.querySelector('.settings-back')!);
    expect(useUiStore.getState().isSettingsOpen).toBe(false);
  });

  it('TimelineView opens a selected note and closes the panel', async () => {
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

    await user.click(screen.getByRole('button', { name: 'Open timeline note Alpha' }));

    expect(onSelectNote).toHaveBeenCalledWith(alpha);
    expect(onClose).toHaveBeenCalled();
  });

  it('GraphView falls back to local wiki links and opens clicked note nodes', async () => {
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

    expect(onSelectNote).toHaveBeenCalledWith(beta);
    expect(onClose).toHaveBeenCalled();
  });

  it('MindMapCanvas renders flow data and saves the current nodes', async () => {
    const user = userEvent.setup();
    const onSave = vi.fn();
    const flowData = branchesToFlowData('Map', [
      { label: 'Branch', color: '#123456', children: ['Leaf'] },
    ]);

    expect(flowData.nodes).toHaveLength(3);
    expect(flowData.edges).toHaveLength(2);

    render(<MindMapCanvas title="Map" initialData={flowData} onSave={onSave} />);

    expect(screen.getByText('3 nodes')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(onSave).toHaveBeenCalledWith(expect.objectContaining({
      nodes: expect.arrayContaining([
        expect.objectContaining({ data: expect.objectContaining({ label: 'Map' }) }),
      ]),
    }));
  });

  it('WorkflowsView closes create and result dialogs with Escape', async () => {
    apiMocks.getWorkflows.mockResolvedValueOnce([]).mockResolvedValueOnce([{
      id: 'wf-1',
      name: 'Flow',
      description: 'desc',
      triggerType: 'manual',
      triggerConfig: '{}',
      steps: '[{"instruction":"do work"}]',
      enabled: true,
      lastRunAt: null,
      createdAt: '2026-06-26T10:00:00Z',
    }]);
    apiMocks.getWorkflowRuns.mockResolvedValueOnce([{
      id: 'run-1',
      workflowId: 'wf-1',
      status: 'completed',
      startedAt: '2026-06-26T10:00:00Z',
      completedAt: '2026-06-26T10:00:05Z',
      results: '[{"step":1,"status":"completed","instruction":"do work","output":"done"}]',
    }]);

    const firstRender = render(<WorkflowsView onClose={vi.fn()} />);
    await waitFor(() => expect(apiMocks.getWorkflows).toHaveBeenCalledTimes(1));

    const createButton = Array.from(firstRender.container.querySelectorAll('button'))
      .find((button) => button.textContent?.includes('+'))!;
    await userEvent.click(createButton);
    expect(screen.getByRole('dialog', { name: 'Create workflow' })).toBeInTheDocument();
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(screen.queryByRole('dialog', { name: 'Create workflow' })).not.toBeInTheDocument();
    firstRender.unmount();

    const secondRender = render(<WorkflowsView onClose={vi.fn()} />);
    await screen.findByText('Flow');

    await userEvent.click(secondRender.container.querySelectorAll('.wf-card-actions .feat-btn')[1]);
    await waitFor(() => expect(apiMocks.getWorkflowRuns).toHaveBeenCalledWith('wf-1'));

    await userEvent.click(secondRender.container.querySelector('.wf-hist-row')!);
    expect(screen.getByRole('dialog', { name: 'Workflow run details' })).toBeInTheDocument();
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(screen.queryByRole('dialog', { name: 'Workflow run details' })).not.toBeInTheDocument();
  });
});
