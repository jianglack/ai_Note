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
import { useNoteEditor } from '../src/hooks/useNoteEditor';
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

    const { result } = renderHook(() => useNoteEditor());

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
    expect(result.current.title).toBe('Edited title');
    expect(useNoteStore.getState().notes[0].title).toBe('Edited title');
    expect(useNoteStore.getState().selectedNote?.title).toBe('Edited title');
  });

  it('syncs selected note editor fields when the note list is refreshed externally', async () => {
    const originalNote: Note = {
      id: 'note-1',
      title: 'Original title',
      content: 'Original body',
      folderId: null,
      createdAt: '2026-06-23T00:00:00Z',
      updatedAt: '2026-06-23T00:00:00Z',
      tags: [],
    };
    const externallyUpdated: Note = {
      ...originalNote,
      updatedAt: '2026-06-23T00:02:00Z',
      tags: [{ id: 'tag-1', name: 'batch-tag' }],
    };

    useNoteStore.setState({ notes: [originalNote], selectedNote: originalNote });

    const { result } = renderHook(() => useNoteEditor());

    await act(async () => {
      await Promise.resolve();
    });
    expect(result.current.tags).toEqual([]);

    act(() => {
      useNoteStore.getState().setNotes([externallyUpdated]);
    });

    await act(async () => {
      await Promise.resolve();
    });

    expect(useNoteStore.getState().selectedNote?.tags.map((tag) => tag.name)).toEqual(['batch-tag']);
    expect(result.current.tags).toEqual(['batch-tag']);
  });
});
