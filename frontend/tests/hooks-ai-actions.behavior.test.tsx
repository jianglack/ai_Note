import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const apiMocks = vi.hoisted(() => ({
  aiChat: vi.fn(),
  aiChatStream: vi.fn(),
  smartChat: vi.fn(),
  routeTask: vi.fn(),
  getChatHistory: vi.fn(),
  getSpiritGreeting: vi.fn(),
  getSmartSuggestions: vi.fn(),
  approvePlan: vi.fn(),
  pausePlan: vi.fn(),
  resumePlan: vi.fn(),
  cancelPlan: vi.fn(),
  rollbackPlan: vi.fn(),
  skipStep: vi.fn(),
  getPlanDetail: vi.fn(),
  subscribePlanProgress: vi.fn(),
  createNote: vi.fn(),
  updateNote: vi.fn(),
  deleteNote: vi.fn(),
  getNotes: vi.fn(),
  getFolders: vi.fn(),
  getTrashNotes: vi.fn(),
  restoreNote: vi.fn(),
  permanentDeleteNote: vi.fn(),
  createFolder: vi.fn(),
  deleteFolder: vi.fn(),
  createSchedule: vi.fn(),
  deleteSchedule: vi.fn(),
}));

vi.mock('../src/api', () => ({
  ...apiMocks,
}));

vi.mock('../src/services/apiBase', () => ({
  getAuthToken: () => 'jwt-token',
}));

import type { Note } from '../src/api';
import { useActions } from '../src/hooks/useActions';
import { useAiChat } from '../src/hooks/useAiChat';
import { useAiStore } from '../src/stores/aiStore';
import { useNoteStore } from '../src/stores/noteStore';
import { useUiStore } from '../src/stores/uiStore';

const note: Note = {
  id: 'note-1',
  title: 'Source note',
  content: 'body',
  folderId: null,
  createdAt: '2026-06-26T00:00:00Z',
  updatedAt: '2026-06-26T00:00:00Z',
  tags: [],
};

describe('useAiChat and useActions behavior', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAiStore.setState({
      messages: [],
      currentMessage: '',
      isTyping: false,
      aiPhase: 'idle',
      aiSteps: [],
      activePlans: [],
    });
    useNoteStore.setState({
      notes: [],
      folders: [],
      selectedNote: null,
      trashNotes: [],
    });
    useUiStore.setState({ status: '', pendingActions: [], showConfirmDialog: false });
  });

  it('loads greeting when chat history is empty', async () => {
    apiMocks.getChatHistory.mockResolvedValueOnce([]);
    apiMocks.getSpiritGreeting.mockResolvedValueOnce('hello');
    apiMocks.getSmartSuggestions.mockResolvedValueOnce([]);
    const { result } = renderHook(() => useAiChat());

    await act(async () => {
      await result.current.loadChatHistory();
    });

    expect(apiMocks.getChatHistory).toHaveBeenCalled();
    expect(useAiStore.getState().messages).toHaveLength(1);
    expect(useAiStore.getState().messages[0]).toMatchObject({ content: 'hello', role: 'spirit' });
  });

  it('streams a direct agent response and refreshes note data', async () => {
    const loadData = vi.fn();
    useNoteStore.setState({
      notes: [note],
      selectedNote: note,
      loadData,
    });
    apiMocks.routeTask.mockResolvedValueOnce({
      route: 'DIRECT_AGENT',
      confidence: 0.9,
      reason: 'simple',
      requiresUserPlanApproval: false,
      estimatedToolSteps: 1,
      riskLevel: 'LOW',
    });
    apiMocks.aiChatStream.mockImplementation(async (
      _message,
      _scope,
      _noteId,
      onToken,
      onComplete,
      _onError,
      onProgress
    ) => {
      onProgress?.('searching', 'Looking');
      onToken('partial');
      onComplete({ content: 'final answer', sources: {} });
    });
    const { result } = renderHook(() => useAiChat());

    await act(async () => {
      await result.current.handleAiMessage('question');
    });

    expect(apiMocks.aiChatStream).toHaveBeenCalledWith(
      'question',
      'selected',
      'note-1',
      expect.any(Function),
      expect.any(Function),
      expect.any(Function),
      expect.any(Function),
      expect.any(AbortSignal)
    );
    expect(useAiStore.getState().messages.map((message) => message.content))
      .toEqual(['question', 'final answer']);
    expect(useAiStore.getState().aiSteps).toEqual([]);
    expect(useAiStore.getState().isTyping).toBe(false);
    expect(loadData).toHaveBeenCalled();
  });

  it('queues an AI action and executes it after confirmation', async () => {
    const created: Note = { ...note, id: 'created-1', title: 'Created note', content: 'Created body' };
    apiMocks.createNote.mockResolvedValueOnce(created);
    const { result } = renderHook(() => useActions());

    await act(async () => {
      await result.current.executeAiAction(JSON.stringify({
        type: 'CREATE_NOTE',
        title: 'Created note',
        content: 'Created body',
      }));
    });

    expect(useUiStore.getState().showConfirmDialog).toBe(true);
    expect(useUiStore.getState().pendingActions).toHaveLength(1);

    await act(async () => {
      await result.current.handleConfirmActions();
    });

    expect(apiMocks.createNote).toHaveBeenCalledWith({
      title: 'Created note',
      content: 'Created body',
      tags: [],
      folderId: null,
    });
    expect(useNoteStore.getState().notes[0]).toMatchObject({ id: 'created-1' });
    expect(useAiStore.getState().aiPhase).toBe('idle');
    expect(useUiStore.getState().pendingActions).toHaveLength(0);
  });
});
