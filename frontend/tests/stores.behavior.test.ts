import { act } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const apiMock = vi.hoisted(() => ({
  post: vi.fn(),
  get: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
  defaults: { baseURL: '' },
  interceptors: {
    request: { use: vi.fn() },
    response: { use: vi.fn() },
  },
}));

vi.mock('../src/services/api', () => ({
  default: apiMock,
}));

import { useAiStore } from '../src/stores/aiStore';
import { useAuthStore } from '../src/stores/authStore';
import { useCardStore } from '../src/stores/cardStore';
import { useNoteStore } from '../src/stores/noteStore';
import { useScheduleStore } from '../src/stores/scheduleStore';
import { useToastStore } from '../src/stores/toastStore';
import { useWorkflowStore } from '../src/stores/workflowStore';

describe('zustand store behavior', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
    vi.useRealTimers();
    useAuthStore.setState({ user: null, isAuthenticated: false, initialized: false });
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
    useScheduleStore.setState({ schedules: [], editingSchedule: undefined });
    useNoteStore.setState({ notes: [], folders: [], selectedNote: null, trashNotes: [] });
    useToastStore.setState({ toasts: [] });
    useWorkflowStore.setState({ cards: {}, activeCardId: null });
    useCardStore.setState({ cards: {} });
  });

  it('authStore uses the server cookie session without persisting a JWT', async () => {
    apiMock.post.mockResolvedValueOnce({
      data: {
        userId: 'user-1',
        username: 'alice',
        email: 'alice@example.com',
      },
    }).mockResolvedValueOnce({ data: {} });

    await act(async () => {
      await useAuthStore.getState().login('alice', 'password');
    });

    expect(localStorage.getItem('token')).toBeNull();
    expect(useAuthStore.getState().isAuthenticated).toBe(true);

    await act(async () => {
      await useAuthStore.getState().logout();
    });

    expect(localStorage.getItem('token')).toBeNull();
    expect(useAuthStore.getState().isAuthenticated).toBe(false);
  });

  it('authStore restores a cookie session through the current-user endpoint', async () => {
    localStorage.setItem('token', 'legacy-token');
    apiMock.get.mockResolvedValueOnce({
      data: { userId: 'user-1', username: 'alice', email: 'alice@example.com' },
    });

    await act(async () => {
      await useAuthStore.getState().initialize();
    });

    expect(apiMock.get).toHaveBeenCalledWith('/api/auth/me');
    expect(localStorage.getItem('token')).toBeNull();
    expect(useAuthStore.getState()).toMatchObject({ isAuthenticated: true, initialized: true });
  });

  it('aiStore appends messages and updates active plans in messages', () => {
    useAiStore.getState().addMessage({
      id: 'msg-1',
      content: 'plan',
      role: 'spirit',
      timestamp: 1,
      planData: {
        id: 'plan-1',
        goal: 'goal',
        status: 'PENDING',
        originalQuery: 'query',
        totalSteps: 1,
        completedSteps: 0,
        createdAt: '',
        updatedAt: '',
        steps: [],
      },
    });
    useAiStore.getState().setActivePlans([{
      id: 'plan-1',
      goal: 'goal',
      status: 'PENDING',
      originalQuery: 'query',
      totalSteps: 1,
      completedSteps: 0,
      createdAt: '',
      updatedAt: '',
      steps: [],
    }]);

    useAiStore.getState().updatePlanInList({
      id: 'plan-1',
      goal: 'goal',
      status: 'COMPLETED',
      originalQuery: 'query',
      totalSteps: 1,
      completedSteps: 1,
      createdAt: '',
      updatedAt: '',
      steps: [],
    });

    expect(useAiStore.getState().activePlans[0].status).toBe('COMPLETED');
    expect(useAiStore.getState().messages[0].planData?.status).toBe('COMPLETED');
  });

  it('scheduleStore adds updates and removes schedules', () => {
    const schedule = {
      id: 'schedule-1',
      title: 'Draft',
      startTime: '2026-06-26T10:00:00Z',
      allDay: false,
      status: 'pending' as const,
      notes: [],
      createdAt: '',
      updatedAt: '',
    };

    useScheduleStore.getState().addSchedule(schedule);
    useScheduleStore.getState().updateScheduleInList({ ...schedule, title: 'Updated' });
    useScheduleStore.getState().removeSchedule('schedule-1');

    expect(useScheduleStore.getState().schedules).toEqual([]);
  });

  it('noteStore deduplicates added and loaded notes by id', () => {
    const original = {
      id: 'note-1',
      title: 'Original',
      content: '',
      tags: [],
      createdAt: '',
      updatedAt: '',
    };
    const updated = { ...original, title: 'Updated' };

    useNoteStore.getState().addNote(original);
    useNoteStore.getState().addNote(updated);
    useNoteStore.getState().setNotes([updated, original]);

    expect(useNoteStore.getState().notes).toHaveLength(1);
    expect(useNoteStore.getState().notes[0].title).toBe('Updated');
  });

  it('toastStore keeps three latest toasts and auto dismisses', async () => {
    vi.useFakeTimers();
    useToastStore.getState().addToast({ type: 'info', title: 'one', duration: 0 });
    useToastStore.getState().addToast({ type: 'info', title: 'two', duration: 0 });
    useToastStore.getState().addToast({ type: 'info', title: 'three', duration: 0 });
    const autoId = useToastStore.getState().addToast({ type: 'success', title: 'auto', duration: 10 });

    expect(useToastStore.getState().toasts.map((toast) => toast.title))
      .toEqual(['two', 'three', 'auto']);

    await vi.advanceTimersByTimeAsync(10);
    expect(useToastStore.getState().toasts.find((toast) => toast.id === autoId)?.exiting).toBe(true);
    await vi.advanceTimersByTimeAsync(300);
    expect(useToastStore.getState().toasts.some((toast) => toast.id === autoId)).toBe(false);
  });

  it('workflowStore moves through confirmation execution and partial failure', () => {
    const cardId = useWorkflowStore.getState().createCard('delete', [
      { noteId: 'note-1', title: 'First' },
      { noteId: 'note-2', title: 'Second' },
    ]);

    useWorkflowStore.getState().toConfirmation(cardId);
    useWorkflowStore.getState().toggleItem(cardId, 'note-2');
    useWorkflowStore.getState().confirm(cardId);
    useWorkflowStore.getState().updateItemStatus(cardId, 'note-1', 'success');
    useWorkflowStore.getState().updateItemStatus(cardId, 'note-2', 'failed', 'boom');
    useWorkflowStore.getState().complete(cardId, { successCount: 1, failedCount: 1 });

    const card = useWorkflowStore.getState().getCard(cardId)!;
    expect(card.state).toBe('partial_failed');
    expect(card.progress.current).toBe(card.progress.total);
    expect(useWorkflowStore.getState().getSelectedItems(cardId)).toHaveLength(1);
  });

  it('cardStore creates and updates card status', () => {
    useCardStore.getState().createCard('card-1', {
      kind: 'confirm',
      title: 'Confirm',
      message: 'Proceed?',
      confirmAction: 'OK',
    });
    useCardStore.getState().updateCardStatus('card-1', 'confirmed', 'done');

    expect(useCardStore.getState().getCard('card-1')).toMatchObject({
      status: 'confirmed',
      result: 'done',
    });
  });
});
