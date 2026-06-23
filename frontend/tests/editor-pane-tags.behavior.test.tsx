import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { Note } from '../src/api';
import EditorPane from '../src/components/EditorPane';
import { useAiStore } from '../src/stores/aiStore';
import { useNoteStore } from '../src/stores/noteStore';
import { useUiStore } from '../src/stores/uiStore';

vi.mock('../src/components/TiptapEditor', () => ({
  default: ({ content, onChange }: { content: string; onChange: (value: string) => void }) => (
    <textarea aria-label="mock editor" value={content} onChange={(event) => onChange(event.target.value)} />
  ),
}));

vi.mock('../src/hooks/useAiChat', () => ({
  useAiChat: () => ({
    handleAiInlineAction: vi.fn(),
    handleAiMessage: vi.fn(),
  }),
}));

vi.mock('../src/hooks/useNotes', () => ({
  useNotes: () => ({
    handleSelectNote: vi.fn(),
    handleCreateNote: vi.fn(),
    handleCloseTrash: vi.fn(),
    handleRestoreNote: vi.fn(),
    handlePermanentDelete: vi.fn(),
  }),
}));

const note: Note = {
  id: 'note-1',
  title: 'Tagged note',
  content: 'Body',
  folderId: null,
  createdAt: '2026-06-23T00:00:00Z',
  updatedAt: '2026-06-23T00:00:00Z',
  tags: [],
};

describe('EditorPane tag editing behavior', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useNoteStore.setState({
      notes: [note],
      folders: [],
      selectedNote: note,
      trashNotes: [],
    });
    useUiStore.setState({ viewMode: 'notes', isAiChatOpen: false, isLinksPanelOpen: false, status: '' });
    useAiStore.setState({ tagSuggestions: [], isLoadingTags: false, inlineSuggestion: null });
  });

  it('reveals the tag input when Add tag is clicked', async () => {
    const user = userEvent.setup();

    render(<EditorPane />);

    const input = document.getElementById('paper-tag-input');
    expect(input).not.toBeNull();
    expect(input).not.toBeVisible();

    await user.click(screen.getByRole('button', { name: '添加标签' }));

    expect(input).toBeVisible();
  });
});
