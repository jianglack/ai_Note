import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import type { Mock } from 'vitest';

const apiMocks = vi.hoisted(() => ({
  getSchedules: vi.fn(),
  getNotes: vi.fn(),
  getFolders: vi.fn(),
  getChatHistory: vi.fn(),
  getSpiritGreeting: vi.fn(),
  getSmartSuggestions: vi.fn(),
  createNote: vi.fn(),
  hybridSearch: vi.fn(),
  getTrashNotes: vi.fn(),
}));

vi.mock('../src/api', async () => {
  const actual = await vi.importActual<typeof import('../src/api')>('../src/api');
  return {
    ...actual,
    getSchedules: apiMocks.getSchedules,
    getNotes: apiMocks.getNotes,
    getFolders: apiMocks.getFolders,
    getChatHistory: apiMocks.getChatHistory,
    getSpiritGreeting: apiMocks.getSpiritGreeting,
    getSmartSuggestions: apiMocks.getSmartSuggestions,
    createNote: apiMocks.createNote,
    hybridSearch: apiMocks.hybridSearch,
    getTrashNotes: apiMocks.getTrashNotes,
  };
});

vi.mock('tippy.js', () => {
  const tippy = vi.fn(() => ({
    destroy: vi.fn(),
    hide: vi.fn(),
    setProps: vi.fn(),
    show: vi.fn(),
  }));
  return { default: tippy, tippy };
});

vi.mock('@tiptap/react', async () => {
  const actual = await vi.importActual<typeof import('@tiptap/react')>('@tiptap/react');
  const React = await import('react');
  return {
    ...actual,
    BubbleMenu: ({ children }: { children: unknown }) =>
      React.createElement(React.Fragment, null, children),
  };
});

import App from '../src/App';
import { useAiStore } from '../src/stores/aiStore';
import { useAuthStore } from '../src/stores/authStore';
import { useNoteStore } from '../src/stores/noteStore';
import { useScheduleStore } from '../src/stores/scheduleStore';
import { useUiStore } from '../src/stores/uiStore';

class TestResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}

const noteFixture = {
  id: 'note-real-1',
  title: 'Real shell note',
  content: 'Full app shell content',
  folderId: null,
  createdAt: '2026-06-30T08:00:00Z',
  updatedAt: '2026-06-30T08:30:00Z',
  tags: [],
};

function renderApp() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route path="/" element={<App />} />
        <Route path="/login" element={<div>login page</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('App shell real behavior', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal('ResizeObserver', TestResizeObserver);
    Element.prototype.scrollTo = vi.fn();
    Element.prototype.scrollIntoView = vi.fn();
    localStorage.clear();

    apiMocks.getSchedules.mockResolvedValue([]);
    apiMocks.getNotes.mockResolvedValue([noteFixture]);
    apiMocks.getFolders.mockResolvedValue([]);
    apiMocks.getChatHistory.mockResolvedValue({ items: [], nextCursor: null, hasMore: false });
    apiMocks.getSpiritGreeting.mockResolvedValue('Hello from the real shell');
    apiMocks.getSmartSuggestions.mockResolvedValue([]);
    apiMocks.createNote.mockResolvedValue({
      ...noteFixture,
      id: 'note-created-1',
      title: 'Untitled',
      content: '',
    });
    apiMocks.hybridSearch.mockResolvedValue([]);
    apiMocks.getTrashNotes.mockResolvedValue([]);

    useAuthStore.setState({
      user: { userId: 'user-1', username: 'alice', email: 'alice@example.com' },
      isAuthenticated: true,
      initialized: true,
      logout: vi.fn(async () => {
        useAuthStore.setState({ user: null, isAuthenticated: false, initialized: true });
      }) as any,
    });
    useAiStore.setState({
      messages: [],
      currentMessage: '',
      isTyping: false,
      aiPhase: 'idle',
      aiSteps: [],
      tagSuggestions: [],
      isLoadingTags: false,
      inlineSuggestion: null,
      activePlans: [],
    });
    useNoteStore.setState({
      notes: [],
      folders: [],
      selectedNote: null,
      trashNotes: [],
    });
    useScheduleStore.setState({ schedules: [], editingSchedule: undefined });
    useUiStore.setState({
      viewMode: 'notes',
      sidebarFilter: 'all',
      selectedFolderId: null,
      status: '',
      searchQuery: '',
      searchResults: [],
      isSearching: false,
      isQuickSwitcherOpen: false,
      isTimelineOpen: false,
      isGraphOpen: false,
      isScheduleFormOpen: false,
      isExtractDialogOpen: false,
      isTracesPanelOpen: false,
      isSettingsOpen: false,
      isMindMapOpen: false,
      isAiChatOpen: false,
      isCostDashboardOpen: false,
      isCanvasOpen: false,
      isWorkflowsOpen: false,
      isEvalDashOpen: false,
      isLinksPanelOpen: false,
      isTaskPanelOpen: false,
      isTaskScheduleOpen: false,
      isAgentMetricsOpen: false,
      pendingActions: [],
      showConfirmDialog: false,
      showNoteSelectionDialog: false,
      noteSelectionContext: null,
      selectedNoteIds: new Set(),
      showClassificationDialog: false,
      classificationResult: null,
      classificationLoading: false,
      selectedClassifications: new Set(),
      showSplitDialog: false,
      splitPreviews: [],
      splitSourceNote: null,
      deleteAfterSplit: false,
      showExportDialog: false,
      exportCandidates: [],
      selectedExportIds: new Set(),
      multiSelectMode: false,
      multiSelectedNoteIds: new Set(),
      showSummaryDialog: false,
      summaryContent: '',
      summaryTargetNote: null,
      summaryLoading: false,
    });
  });

  it('loads backend data into the real sidebar and note list shell', async () => {
    renderApp();

    await waitFor(() => expect(apiMocks.getSchedules).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(apiMocks.getNotes).toHaveBeenCalledTimes(1));
    expect(apiMocks.getFolders).toHaveBeenCalledTimes(1);
    expect(apiMocks.getChatHistory).toHaveBeenCalledTimes(1);
    expect(apiMocks.getSpiritGreeting).toHaveBeenCalledTimes(1);

    expect(await screen.findByRole('listbox', { name: 'Notes' })).toBeInTheDocument();
    expect(await screen.findByText('Real shell note')).toBeInTheDocument();
    expect(screen.getByText('Full app shell content')).toBeInTheDocument();
  });

  it('opens the real quick switcher and logs out through router navigation', async () => {
    const user = userEvent.setup();
    renderApp();
    await screen.findByText('Real shell note');

    fireEvent.keyDown(window, { key: 'p', ctrlKey: true });
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    expect(screen.getAllByText('Real shell note').length).toBeGreaterThan(1);

    await user.click(screen.getByLabelText('Log out'));

    await waitFor(() => {
      expect((useAuthStore.getState().logout as Mock)).toHaveBeenCalledTimes(1);
    });
    expect(await screen.findByText('login page')).toBeInTheDocument();
  });

  it('creates a note through the real empty-state create flow', async () => {
    const user = userEvent.setup();
    renderApp();
    await waitFor(() => expect(apiMocks.getNotes).toHaveBeenCalledTimes(1));

    await user.click(screen.getByRole('button', { name: 'Create blank note' }));

    await waitFor(() => expect(apiMocks.createNote).toHaveBeenCalled());
    expect(useNoteStore.getState().selectedNote?.id).toBe('note-created-1');
    expect(useUiStore.getState().status).toBe('Note created');
  });
});
