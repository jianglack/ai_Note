import { act, render, renderHook, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const apiMocks = vi.hoisted(() => ({
  createNote: vi.fn(),
  updateNote: vi.fn(),
  deleteNote: vi.fn(),
  permanentDeleteNote: vi.fn(),
  getNotes: vi.fn(),
  getFolders: vi.fn(),
  getTrashNotes: vi.fn(),
  createFolder: vi.fn(),
  batchDeleteNotes: vi.fn(),
  batchPermanentDeleteNotes: vi.fn(),
  batchRestoreNotes: vi.fn(),
  batchMoveNotes: vi.fn(),
  batchAddTag: vi.fn(),
  batchRemoveTag: vi.fn(),
  batchArchiveNotes: vi.fn(),
  createSchedule: vi.fn(),
  updateScheduleStatus: vi.fn(),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
  classifyNotes: vi.fn(),
}));

const dialogMocks = vi.hoisted(() => ({
  askConfirm: vi.fn(),
}));

vi.mock('../src/api', () => ({
  createNote: apiMocks.createNote,
  updateNote: apiMocks.updateNote,
  deleteNote: apiMocks.deleteNote,
  permanentDeleteNote: apiMocks.permanentDeleteNote,
  getNotes: apiMocks.getNotes,
  getFolders: apiMocks.getFolders,
  getTrashNotes: apiMocks.getTrashNotes,
  createFolder: apiMocks.createFolder,
  batchDeleteNotes: apiMocks.batchDeleteNotes,
  batchPermanentDeleteNotes: apiMocks.batchPermanentDeleteNotes,
  batchRestoreNotes: apiMocks.batchRestoreNotes,
  batchMoveNotes: apiMocks.batchMoveNotes,
  batchAddTag: apiMocks.batchAddTag,
  batchRemoveTag: apiMocks.batchRemoveTag,
  batchArchiveNotes: apiMocks.batchArchiveNotes,
  createSchedule: apiMocks.createSchedule,
  updateScheduleStatus: apiMocks.updateScheduleStatus,
  markNotificationRead: apiMocks.markNotificationRead,
  markAllNotificationsRead: apiMocks.markAllNotificationsRead,
  classifyNotes: apiMocks.classifyNotes,
}));

vi.mock('../src/services/dialogService', () => ({
  askConfirm: dialogMocks.askConfirm,
}));

import TrashView from '../src/TrashView';
import { useCardActions } from '../src/hooks/useCardActions';
import { useDialogs } from '../src/hooks/useDialogs';
import { useWorkflowExecution } from '../src/hooks/useWorkflowExecution';
import { useAiStore } from '../src/stores/aiStore';
import { useCardStore } from '../src/stores/cardStore';
import { useNoteStore } from '../src/stores/noteStore';
import { useToastStore } from '../src/stores/toastStore';
import { useUiStore } from '../src/stores/uiStore';
import { useWorkflowStore } from '../src/stores/workflowStore';
import type { Note } from '../src/api';

function makeNote(overrides: Partial<Note> = {}): Note {
  return {
    id: 'note-1',
    title: 'Alpha',
    content: 'Preview body',
    folderId: null,
    createdAt: '2026-06-26T10:00:00Z',
    updatedAt: '2026-06-26T10:15:00Z',
    tags: [],
    ...overrides,
  };
}

describe('workflow dialogs card actions and trash behavior', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
    useNoteStore.setState({ notes: [], folders: [], selectedNote: null, trashNotes: [] });
    useUiStore.setState({
      status: '',
      showNoteSelectionDialog: false,
      noteSelectionContext: null,
      selectedNoteIds: new Set(),
    });
    useWorkflowStore.setState({ cards: {}, activeCardId: null });
    useToastStore.setState({ toasts: [] });
    useAiStore.setState({ aiPhase: 'idle', messages: [] });
    useCardStore.setState({ cards: {} });
  });

  it('useDialogs returns selected delete actions and clears selection state', async () => {
    const alpha = makeNote();
    useUiStore.setState({
      showNoteSelectionDialog: true,
      noteSelectionContext: {
        operation: 'delete',
        message: 'choose notes',
        candidates: [alpha],
      },
      selectedNoteIds: new Set(['note-1']),
    });

    const { result } = renderHook(() => useDialogs());

    let actionJson: string | undefined;
    await act(async () => {
      actionJson = await result.current.handleConfirmNoteSelection();
    });

    expect(JSON.parse(actionJson!)).toEqual([{ type: 'DELETE_NOTE', noteId: 'note-1' }]);
    expect(useUiStore.getState().showNoteSelectionDialog).toBe(false);
    expect(useUiStore.getState().noteSelectionContext).toBeNull();
    expect(useUiStore.getState().selectedNoteIds.size).toBe(0);
  });

  it('useWorkflowExecution creates a confirmation workflow and completes selected notes', async () => {
    useNoteStore.setState({ notes: [makeNote()], trashNotes: [] });
    apiMocks.batchDeleteNotes.mockResolvedValueOnce({
      successCount: 1,
      failedCount: 0,
      details: [{ noteId: 'note-1', status: 'success' }],
    });
    apiMocks.getNotes.mockResolvedValueOnce([]);
    apiMocks.getTrashNotes.mockResolvedValueOnce([]);

    const { result } = renderHook(() => useWorkflowExecution());

    let cardId: string | null = null;
    act(() => {
      cardId = result.current.createWorkflowFromActions(JSON.stringify([
        { type: 'DELETE_NOTE', noteId: 'note-1', title: 'Alpha' },
      ]));
    });

    expect(cardId).toBeTruthy();
    expect(useWorkflowStore.getState().cards[cardId!].state).toBe('awaiting_confirmation');

    await act(async () => {
      await result.current.executeWorkflowCard(cardId!);
    });

    expect(apiMocks.batchDeleteNotes).toHaveBeenCalledWith(['note-1']);
    expect(useWorkflowStore.getState().cards[cardId!].state).toBe('done');
    expect(useToastStore.getState().toasts[0].type).toBe('success');
    expect(useAiStore.getState().aiPhase).toBe('idle');
  });

  it('useCardActions completes schedule suggestions and persists dismissal', async () => {
    apiMocks.updateScheduleStatus.mockResolvedValueOnce(undefined);
    useCardStore.getState().createCard('card-1', {
      kind: 'suggestion',
      suggestionKind: 'expired',
      text: 'Complete schedule',
      buttons: [],
    });

    const { result } = renderHook(() => useCardActions());

    await act(async () => {
      await result.current.handleCardAction('card-1', 'COMPLETE_SCHEDULE:schedule-1');
    });

    expect(apiMocks.updateScheduleStatus).toHaveBeenCalledWith('schedule-1', 'completed');
    expect(useCardStore.getState().cards['card-1'].status).toBe('confirmed');
    expect(localStorage.getItem('ainote_dismissed_suggestions')).toContain('Complete schedule');
  });

  it('TrashView restores notes and confirms permanent deletion', async () => {
    const user = userEvent.setup();
    dialogMocks.askConfirm.mockResolvedValueOnce(true);
    const onClose = vi.fn();
    const onRestore = vi.fn();
    const onPermanentDelete = vi.fn();

    const { container } = render(
      <TrashView
        notes={[makeNote({ tags: [{ id: 'tag-1', name: 'urgent' }] })]}
        onClose={onClose}
        onRestore={onRestore}
        onPermanentDelete={onPermanentDelete}
      />,
    );

    expect(screen.getByText('Alpha')).toBeInTheDocument();
    expect(screen.getByText('urgent')).toBeInTheDocument();

    await user.click(container.querySelector('.restore-button')!);
    expect(onRestore).toHaveBeenCalledWith('note-1');

    await user.click(container.querySelector('.delete-button')!);
    expect(dialogMocks.askConfirm).toHaveBeenCalledWith(expect.objectContaining({ danger: true }));
    expect(onPermanentDelete).toHaveBeenCalledWith('note-1');

    await user.click(screen.getByLabelText('Close trash'));
    expect(onClose).toHaveBeenCalled();
  });
});
