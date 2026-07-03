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
    apiMocks.getChatHistory.mockResolvedValueOnce({ items: [], nextCursor: null, hasMore: false });
    apiMocks.getSpiritGreeting.mockResolvedValueOnce('hello');
    apiMocks.getSmartSuggestions.mockResolvedValueOnce([]);
    const { result } = renderHook(() => useAiChat());

    await act(async () => {
      await result.current.loadChatHistory();
    });

    expect(apiMocks.getChatHistory).toHaveBeenCalledWith({ limit: 100 });
    expect(result.current.hasMoreHistory).toBe(false);
    expect(useAiStore.getState().messages).toHaveLength(1);
    expect(useAiStore.getState().messages[0]).toMatchObject({ content: 'hello', role: 'spirit' });
  });

  it('prepends older chat history when requested', async () => {
    apiMocks.getChatHistory
      .mockResolvedValueOnce({
        items: [{ id: 'm2', role: 'assistant', content: 'latest', createdAt: '2026-07-03T10:00:00' }],
        nextCursor: 'm2',
        hasMore: true,
      })
      .mockResolvedValueOnce({
        items: [{ id: 'm1', role: 'user', content: 'older', createdAt: '2026-07-03T09:00:00' }],
        nextCursor: null,
        hasMore: false,
      });
    apiMocks.getSmartSuggestions.mockResolvedValueOnce([]);
    const { result } = renderHook(() => useAiChat());

    await act(async () => {
      await result.current.loadChatHistory();
    });

    expect(useAiStore.getState().messages.map((message) => message.content)).toEqual(['latest']);
    expect(result.current.hasMoreHistory).toBe(true);

    await act(async () => {
      await result.current.loadOlderChatHistory();
    });

    expect(apiMocks.getChatHistory).toHaveBeenLastCalledWith({ limit: 100, before: 'm2' });
    expect(useAiStore.getState().messages.map((message) => message.content)).toEqual(['older', 'latest']);
    expect(result.current.hasMoreHistory).toBe(false);
  });

  it('uses smart chat for a direct agent response and refreshes note data', async () => {
    const loadData = vi.fn();
    useNoteStore.setState({
      notes: [note],
      selectedNote: note,
      loadData,
    });
    apiMocks.smartChat.mockResolvedValueOnce({
      type: 'direct',
      route: 'DIRECT_AGENT',
      content: 'final answer',
      sources: {},
    });
    const { result } = renderHook(() => useAiChat());

    await act(async () => {
      await result.current.handleAiMessage('question');
    });

    expect(apiMocks.routeTask).not.toHaveBeenCalled();
    expect(apiMocks.smartChat).toHaveBeenCalledWith('question', ['note-1'], false);
    expect(apiMocks.aiChatStream).not.toHaveBeenCalled();
    expect(useAiStore.getState().messages.map((message) => message.content))
      .toEqual(['question', 'final answer']);
    expect(useAiStore.getState().aiSteps).toEqual([]);
    expect(useAiStore.getState().isTyping).toBe(false);
    expect(loadData).toHaveBeenCalled();
  });

  it('renders a plan when smart chat creates one', async () => {
    apiMocks.smartChat.mockResolvedValueOnce({
      type: 'plan_created',
      route: 'PLANNED_TASK',
      routeDecision: {
        route: 'PLANNED_TASK',
        confidence: 0.95,
        reason: 'multi step',
        requiresUserPlanApproval: true,
        estimatedToolSteps: 4,
        riskLevel: 'MEDIUM',
      },
      plan: {
        id: 'plan-1',
        goal: '整理笔记',
        status: 'AWAITING_APPROVAL',
        originalQuery: '整理',
        totalSteps: 1,
        completedSteps: 0,
        createdAt: '2026-07-03T00:00:00',
        updatedAt: '2026-07-03T00:00:00',
        steps: [],
      },
    });
    const { result } = renderHook(() => useAiChat());

    await act(async () => {
      await result.current.handleAiMessage('整理');
    });

    expect(apiMocks.routeTask).not.toHaveBeenCalled();
    expect(apiMocks.smartChat).toHaveBeenCalledWith('整理', [], false);
    expect(useAiStore.getState().messages.at(-1)?.planData?.id).toBe('plan-1');
    expect(useAiStore.getState().isTyping).toBe(false);
  });

  it('renders pending action from direct smart chat response', async () => {
    apiMocks.smartChat.mockResolvedValueOnce({
      type: 'direct',
      route: 'DIRECT_AGENT',
      content: '需要你确认一下',
      sources: {},
      actionJson: '[{"type":"DELETE_NOTES","scope":"ALL_ACTIVE_NOTES","count":"31"}]',
    });
    const { result } = renderHook(() => useAiChat());

    await act(async () => {
      await result.current.handleAiMessage('删除全部笔记');
    });

    expect(apiMocks.routeTask).not.toHaveBeenCalled();
    expect(useAiStore.getState().messages.at(-1)?.pendingAction?.actionType).toBe('deleteNotes');
    expect(useAiStore.getState().messages.at(-1)?.pendingAction?.actionJson)
      .toContain('ALL_ACTIVE_NOTES');
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
