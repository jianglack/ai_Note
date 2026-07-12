import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

const apiMocks = vi.hoisted(() => ({
  getSchedules: vi.fn(),
}));

const hookMocks = vi.hoisted(() => ({
  loadChatHistory: vi.fn(),
  handleAiMessage: vi.fn(),
  handleCancelAi: vi.fn(),
  handlePlanAction: vi.fn(),
  handleCardAction: vi.fn(),
  loadData: vi.fn(),
  handleSelectNote: vi.fn(),
  handleCreateNote: vi.fn(),
  logout: vi.fn(),
}));

vi.mock('../src/api', () => ({
  getSchedules: apiMocks.getSchedules,
}));

vi.mock('../src/hooks/useAiChat', () => ({
  useAiChat: () => ({
    loadChatHistory: hookMocks.loadChatHistory,
    handleAiMessage: hookMocks.handleAiMessage,
    handleCancelAi: hookMocks.handleCancelAi,
    handlePlanAction: hookMocks.handlePlanAction,
  }),
}));

vi.mock('../src/hooks/useCardActions', () => ({
  useCardActions: () => ({
    handleCardAction: hookMocks.handleCardAction,
  }),
}));

vi.mock('../src/hooks/useNotes', () => ({
  useNotes: () => ({
    loadData: hookMocks.loadData,
    handleSelectNote: hookMocks.handleSelectNote,
    handleCreateNote: hookMocks.handleCreateNote,
  }),
}));

vi.mock('../src/components/Sidebar', () => ({
  default: ({ onLogout }: { onLogout: () => void }) => (
    <button type="button" onClick={onLogout}>mock logout</button>
  ),
}));
vi.mock('../src/components/NoteListPanel', () => ({
  default: () => <div>note-list-panel</div>,
}));
vi.mock('../src/components/EditorPane', () => ({
  default: () => <div>editor-pane</div>,
}));
vi.mock('../src/components/chat/AiChatPanel', () => ({
  default: () => <div>ai-chat-panel</div>,
}));
vi.mock('../src/components/QuickSwitcher', () => ({
  default: ({ isOpen }: { isOpen: boolean }) => (
    isOpen ? <div data-testid="quick-switcher">quick-switcher</div> : null
  ),
}));
vi.mock('../src/components/MindMapCanvas', () => ({
  default: () => <div>mind-map-canvas</div>,
  branchesToFlowData: vi.fn(() => ({ nodes: [], edges: [] })),
}));
vi.mock('../src/components/AppDialogHost', () => ({ default: () => <div>dialog-host</div> }));
vi.mock('../src/components/ConfirmDialogs', () => ({ default: () => <div>confirm-dialogs</div> }));
vi.mock('../src/components/toast/ToastContainer', () => ({ default: () => <div>toast-container</div> }));

vi.mock('../src/components/TimelineView', () => ({ default: () => <div>timeline-view</div> }));
vi.mock('../src/components/GraphView', () => ({ default: () => <div>graph-view</div> }));
vi.mock('../src/components/ScheduleForm', () => ({ default: () => <div>schedule-form</div> }));
vi.mock('../src/components/ScheduleExtractDialog', () => ({ default: () => <div>schedule-extract-dialog</div> }));
vi.mock('../src/components/TracesPanel', () => ({ default: () => <div>traces-panel</div> }));
vi.mock('../src/components/SettingsPage', () => ({ default: () => <div>settings-page</div> }));
vi.mock('../src/components/admin/CostDashboard', () => ({ default: () => <div>cost-dashboard</div> }));
vi.mock('../src/components/admin/AgentMetricsDashboard', () => ({ default: () => <div>agent-metrics-dashboard</div> }));
vi.mock('../src/components/features/CanvasView', () => ({ default: () => <div>canvas-view</div> }));
vi.mock('../src/components/features/WorkflowsView', () => ({ default: () => <div>workflows-view</div> }));
vi.mock('../src/components/features/EvalDashView', () => ({ default: () => <div>eval-dash-view</div> }));
vi.mock('../src/components/features/TaskPanelView', () => ({ default: () => <div>task-panel-view</div> }));
vi.mock('../src/components/features/TaskScheduleView', () => ({ default: () => <div>task-schedule-view</div> }));

import App from '../src/App';
import { useAiStore } from '../src/stores/aiStore';
import { useAuthStore } from '../src/stores/authStore';
import { useNoteStore } from '../src/stores/noteStore';
import { useScheduleStore } from '../src/stores/scheduleStore';
import { useUiStore } from '../src/stores/uiStore';

describe('App behavior', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    hookMocks.logout.mockResolvedValue(undefined);
    apiMocks.getSchedules.mockResolvedValue([{
      id: 'schedule-1',
      title: 'Planning',
      startTime: '2026-06-26T10:00:00Z',
      allDay: false,
      status: 'pending',
      notes: [],
      createdAt: '2026-06-26T09:00:00Z',
      updatedAt: '2026-06-26T09:00:00Z',
    }]);
    useAuthStore.setState({
      user: { userId: 'user-1', username: 'alice', email: 'alice@example.com' },
      isAuthenticated: true,
      initialized: true,
      logout: hookMocks.logout as any,
    });
    useAiStore.setState({
      messages: [],
      currentMessage: '',
      isTyping: false,
      aiPhase: 'idle',
      aiSteps: [],
    });
    useNoteStore.setState({
      notes: [{
        id: 'note-1',
        title: 'Alpha',
        content: 'Body',
        folderId: null,
        createdAt: '2026-06-26T09:00:00Z',
        updatedAt: '2026-06-26T09:00:00Z',
        tags: [],
      }],
      selectedNote: null,
      folders: [],
      trashNotes: [],
    });
    useScheduleStore.setState({ schedules: [], editingSchedule: undefined });
    useUiStore.setState({
      status: '',
      isQuickSwitcherOpen: false,
      isTimelineOpen: false,
      isGraphOpen: false,
      isScheduleFormOpen: false,
      isExtractDialogOpen: false,
      isTracesPanelOpen: false,
      isSettingsOpen: false,
      isMindMapOpen: false,
      isAiChatOpen: true,
      isCostDashboardOpen: false,
      isCanvasOpen: false,
      isWorkflowsOpen: false,
      isEvalDashOpen: false,
      isTaskPanelOpen: false,
      isTaskScheduleOpen: false,
      isAgentMetricsOpen: false,
    });
  });

  it('loads startup data, opens global panels, and logs out to login', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<App />} />
          <Route path="/login" element={<div>login page</div>} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => expect(apiMocks.getSchedules).toHaveBeenCalledTimes(1));
    expect(useScheduleStore.getState().schedules).toHaveLength(1);
    expect(hookMocks.loadData).toHaveBeenCalled();
    expect(hookMocks.loadChatHistory).toHaveBeenCalled();
    expect(screen.getByText('ai-chat-panel')).toBeInTheDocument();

    fireEvent.keyDown(window, { key: 'p', ctrlKey: true });
    expect(await screen.findByTestId('quick-switcher')).toBeInTheDocument();

    act(() => {
      useUiStore.getState().setIsCanvasOpen(true);
    });
    expect(await screen.findByText('canvas-view')).toBeInTheDocument();

    await user.click(screen.getByText('mock logout'));
    expect(hookMocks.logout).toHaveBeenCalled();
    expect(await screen.findByText('login page')).toBeInTheDocument();
  });
});
