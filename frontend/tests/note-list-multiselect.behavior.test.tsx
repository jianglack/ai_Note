import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { Note } from '../src/api';
import NoteListPanel from '../src/components/NoteListPanel';
import { useNoteStore } from '../src/stores/noteStore';
import { useUiStore } from '../src/stores/uiStore';

const note: Note = {
  id: 'note-1',
  title: 'Selectable note',
  content: 'Body',
  folderId: null,
  createdAt: '2026-06-23T00:00:00Z',
  updatedAt: '2026-06-23T00:00:00Z',
  tags: [],
};

describe('NoteListPanel multi-select behavior', () => {
  beforeEach(() => {
    globalThis.ResizeObserver = class ResizeObserver {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
    useNoteStore.setState({
      notes: [note],
      folders: [],
      selectedNote: note,
      trashNotes: [],
    });
    useUiStore.setState({
      sidebarFilter: 'all',
      selectedFolderId: null,
      multiSelectMode: false,
      multiSelectedNoteIds: new Set(),
    });
  });

  it('selects a single note when its row checkbox is clicked', async () => {
    const user = userEvent.setup();

    const { container } = render(
      <NoteListPanel
        notes={[note]}
        selectedNoteId={note.id}
        onSelectNote={vi.fn()}
        onCreateNote={vi.fn()}
      />
    );

    await user.click(screen.getByRole('button', { name: '多选' }));
    const rowCheckbox = container.querySelector('.note-list-item button.shrink-0');
    expect(rowCheckbox).not.toBeNull();

    await user.click(rowCheckbox!);

    expect(useUiStore.getState().multiSelectedNoteIds.has(note.id)).toBe(true);
  });
});
