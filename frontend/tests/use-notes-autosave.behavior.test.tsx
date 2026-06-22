import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const apiMocks = vi.hoisted(() => ({
  getNotes: vi.fn(),
  getFolders: vi.fn(),
  createNote: vi.fn(),
  updateNote: vi.fn(),
  deleteNote: vi.fn(),
  createFolder: vi.fn(),
  updateFolder: vi.fn(),
  deleteFolder: vi.fn(),
  getTrashNotes: vi.fn(),
  restoreNote: vi.fn(),
  permanentDeleteNote: vi.fn(),
  hybridSearch: vi.fn(),
  getSpiritSuggestTags: vi.fn(),
}));

vi.mock('../src/api', () => ({
  ...apiMocks,
}));

vi.mock('../src/services/dialogService', () => ({
  askConfirm: vi.fn(),
  askPrompt: vi.fn(),
}));

import type { Note } from '../src/api';
import { updateNote } from '../src/api';
import { useNotes } from '../src/hooks/useNotes';
import { useNoteStore } from '../src/stores/noteStore';
import { useUiStore } from '../src/stores/uiStore';

describe('useNotes autosave behavior', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
    useNoteStore.setState({
      notes: [],
      folders: [],
      selectedNote: null,
      trashNotes: [],
    });
    useUiStore.setState({ status: '' });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('debounces note updates and writes the selected note fields', async () => {
    const originalNote: Note = {
      id: 'note-1',
      title: 'Original title',
      content: 'Original body',
      folderId: null,
      createdAt: '2026-06-23T00:00:00Z',
      updatedAt: '2026-06-23T00:00:00Z',
      tags: [],
    };
    const updatedNote: Note = {
      ...originalNote,
      title: 'Edited title',
      updatedAt: '2026-06-23T00:01:00Z',
    };

    vi.mocked(updateNote).mockResolvedValue(updatedNote);
    useNoteStore.setState({ notes: [originalNote], selectedNote: originalNote });

    const { result } = renderHook(() => useNotes());

    await act(async () => {
      await Promise.resolve();
    });
    expect(result.current.title).toBe('Original title');
    vi.mocked(updateNote).mockClear();

    act(() => {
      result.current.setTitle('Edited title');
    });

    await act(async () => {
      await vi.advanceTimersByTimeAsync(799);
    });
    expect(updateNote).not.toHaveBeenCalled();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1);
    });

    expect(updateNote).toHaveBeenCalledWith('note-1', {
      title: 'Edited title',
      content: 'Original body',
      tags: [],
      folderId: null,
    });
    expect(useNoteStore.getState().selectedNote?.title).toBe('Edited title');
  });
});
