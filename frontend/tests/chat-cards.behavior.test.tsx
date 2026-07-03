import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AiChatPanel from '../src/components/chat/AiChatPanel';
import AiCardRenderer from '../src/components/chat/cards/AiCardRenderer';
import PendingActionCard from '../src/components/chat/cards/PendingActionCard';
import AiWorkflowCard from '../src/components/workflow/AiWorkflowCard';
import type { CardState } from '../src/components/chat/cards/types';
import { useAiStore } from '../src/stores/aiStore';
import { useNoteStore } from '../src/stores/noteStore';
import { useWorkflowStore } from '../src/stores/workflowStore';

const apiMocks = vi.hoisted(() => ({
  sendActionFeedback: vi.fn(),
  saveChatMessages: vi.fn(),
}));

vi.mock('../src/api', async () => {
  const actual = await vi.importActual<typeof import('../src/api')>('../src/api');
  return {
    ...actual,
    sendActionFeedback: apiMocks.sendActionFeedback,
    saveChatMessages: apiMocks.saveChatMessages,
  };
});

class TestResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}

describe('chat and card behavior', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal('ResizeObserver', TestResizeObserver);
    Element.prototype.scrollIntoView = vi.fn();
    useAiStore.setState({
      messages: [],
      currentMessage: '',
      isTyping: false,
      aiPhase: 'idle',
      aiSteps: [],
    });
    useWorkflowStore.setState({ cards: {}, activeCardId: null });
    useNoteStore.setState({
      notes: [],
      folders: [],
      selectedNote: null,
      trashNotes: [],
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('renders chat messages, source links, quick commands, and input send/cancel behavior', async () => {
    const onSendMessage = vi.fn();
    const onCancel = vi.fn();
    const onClose = vi.fn();
    const onNoteClick = vi.fn();

    const { container, rerender } = render(
      <AiChatPanel
        messages={[
          { id: 'user-1', role: 'user', content: 'Find this', timestamp: 1 },
          {
            id: 'ai-1',
            role: 'spirit',
            content: 'Answer with **markdown**',
            timestamp: 2,
            degraded: true,
            degradationReason: 'fallback',
            sources: { 1: { id: 'note-1', title: 'Source note' } },
          },
        ]}
        currentMessage=""
        isTyping={false}
        aiPhase="idle"
        onSendMessage={onSendMessage}
        onCancel={onCancel}
        onClose={onClose}
        onNoteClick={onNoteClick}
        noteCount={2}
      />,
    );

    expect(screen.getByRole('complementary', { name: 'AI chat assistant' })).toBeInTheDocument();
    expect(screen.getByText('Find this')).toBeInTheDocument();
    expect(screen.getByText('markdown')).toBeInTheDocument();
    fireEvent.click(screen.getByText('[1] Source note'));
    expect(onNoteClick).toHaveBeenCalledWith('note-1');

    const input = screen.getByRole('textbox');
    fireEvent.change(input, { target: { value: 'Summarize notes' } });
    fireEvent.keyDown(input, { key: 'Enter' });
    expect(onSendMessage).toHaveBeenCalledWith('Summarize notes');

    fireEvent.click(container.querySelectorAll('button')[3]);
    expect(onSendMessage).toHaveBeenCalledTimes(2);

    rerender(
      <AiChatPanel
        messages={[]}
        currentMessage=""
        isTyping
        aiPhase="thinking"
        aiSteps={[{ step: 'searching', detail: 'Searching notes' }]}
        onSendMessage={onSendMessage}
        onCancel={onCancel}
        onClose={onClose}
      />,
    );
    expect(screen.getByRole('status')).toBeInTheDocument();
    fireEvent.keyDown(screen.getByRole('textbox'), { key: 'Enter' });
    expect(onCancel).toHaveBeenCalled();

    fireEvent.click(container.querySelectorAll('button')[2]);
    expect(onClose).toHaveBeenCalled();
  });

  it('routes interactive card actions for every card payload kind', () => {
    const onAction = vi.fn();

    const multi: CardState = {
      status: 'pending',
      payload: {
        kind: 'multi-select',
        title: 'Pick notes',
        items: [{ id: 'a', name: 'Alpha' }, { id: 'b', name: 'Beta', tag: 'review' }],
        defaultSelected: ['a'],
        confirmLabel: 'Apply',
        action: 'batchTag',
      },
    };
    const { container, rerender } = render(<AiCardRenderer card={multi} onAction={onAction} />);
    fireEvent.click(screen.getByText('Beta'));
    fireEvent.click(container.querySelector('.cc-btn.primary') as HTMLElement);
    expect(onAction).toHaveBeenLastCalledWith('batchTag', { selectedIds: ['a', 'b'] });

    rerender(<AiCardRenderer card={{
      status: 'pending',
      payload: {
        kind: 'single-select',
        title: 'Choose folder',
        items: [{ id: 'folder-1', name: 'Projects', recommend: true }],
        action: 'move',
      },
    }} onAction={onAction} />);
    fireEvent.click(screen.getByText('Projects'));
    fireEvent.click(container.querySelector('.cc-btn.primary') as HTMLElement);
    expect(onAction).toHaveBeenLastCalledWith('move', { selectedId: 'folder-1' });

    rerender(<AiCardRenderer card={{
      status: 'pending',
      payload: {
        kind: 'text-input',
        title: 'Create note',
        initialTitle: 'Draft',
        initialNotebook: { id: 'folder-1', name: 'Projects' },
        initialTags: ['planning'],
        action: 'createNote',
      },
    }} onAction={onAction} />);
    fireEvent.change(container.querySelector('.cc-input') as HTMLInputElement, { target: { value: 'Updated Draft' } });
    fireEvent.click(container.querySelector('.cc-btn.primary') as HTMLElement);
    expect(onAction).toHaveBeenLastCalledWith('createNote', {
      title: 'Updated Draft',
      notebookId: 'folder-1',
      tags: ['planning'],
    });

    rerender(<AiCardRenderer card={{
      status: 'pending',
      payload: {
        kind: 'schedule',
        initial: { title: 'Review', date: '2026-06-30', time: '09:00', remind: '15' },
        action: 'createSchedule',
      },
    }} onAction={onAction} />);
    fireEvent.click(container.querySelector('.cc-select-display') as HTMLElement);
    fireEvent.click(container.querySelector('.cc-btn.primary') as HTMLElement);
    expect(onAction).toHaveBeenLastCalledWith('createSchedule', {
      title: 'Review',
      date: '2026-06-30',
      time: '09:00',
      remind: '30',
    });

    rerender(<AiCardRenderer card={{
      status: 'pending',
      payload: {
        kind: 'confirm',
        title: 'Delete note',
        description: 'Move to trash',
        confirmLabel: 'Delete',
        action: 'deleteNote',
        params: { noteId: 'note-1' },
      },
    }} onAction={onAction} />);
    fireEvent.click(container.querySelector('.cc-btn.danger') as HTMLElement);
    expect(onAction).toHaveBeenLastCalledWith('deleteNote', { noteId: 'note-1' });

    rerender(<AiCardRenderer card={{
      status: 'pending',
      payload: {
        kind: 'tag-picker',
        title: 'Tags',
        noteId: 'note-1',
        noteName: 'Alpha',
        tags: ['planning', 'review'],
        defaultSelected: ['planning'],
        action: 'addTags',
      },
    }} onAction={onAction} />);
    fireEvent.click(screen.getByText('#review'));
    fireEvent.click(container.querySelector('.cc-btn.primary') as HTMLElement);
    expect(onAction).toHaveBeenLastCalledWith('addTags', {
      noteId: 'note-1',
      tags: ['planning', 'review'],
    });

    rerender(<AiCardRenderer card={{
      status: 'confirmed',
      result: 'Done',
      payload: multi.payload,
    }} onAction={onAction} />);
    expect(screen.getByText('Done')).toBeInTheDocument();
  });

  it('renders all provided messages and exposes the older-history control', () => {
    const messages = Array.from({ length: 55 }, (_, index) => ({
      id: `msg-${index + 1}`,
      role: index % 2 === 0 ? 'user' as const : 'spirit' as const,
      content: `history message ${index + 1}`,
      timestamp: index + 1,
    }));
    const onLoadOlderHistory = vi.fn();

    render(
      <AiChatPanel
        messages={messages}
        currentMessage=""
        isTyping={false}
        aiPhase="idle"
        onSendMessage={vi.fn()}
        onCancel={vi.fn()}
        onClose={vi.fn()}
        hasMoreHistory
        isLoadingOlderHistory={false}
        onLoadOlderHistory={onLoadOlderHistory}
      />,
    );

    expect(screen.getByText('history message 1')).toBeInTheDocument();
    expect(screen.getByText('history message 55')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '加载更早的聊天记录' }));
    expect(onLoadOlderHistory).toHaveBeenCalled();
  });

  it('sends PENDING_ACTION feedback, refreshes notes, persists the follow-up, and appends it after delay', async () => {
    vi.useFakeTimers();
    apiMocks.sendActionFeedback.mockResolvedValue({ content: 'Action handled' });
    apiMocks.saveChatMessages.mockResolvedValue(undefined);
    const loadData = vi.fn().mockResolvedValue(undefined);
    useNoteStore.setState({ loadData });

    const { container } = render(
      <PendingActionCard
        actionType="deleteNote"
        actionDescription="Delete Alpha"
        actionJson='{"action":"deleteNote","noteId":"note-1"}'
        details={[{ label: 'Alpha' }, { label: 'Cannot undo', warn: true }]}
        irreversible
      />,
    );

    await act(async () => {
      fireEvent.click(container.querySelector('.pac-btn.primary') as HTMLElement);
    });
    expect(apiMocks.sendActionFeedback).toHaveBeenCalledWith({
      actionJson: '{"action":"deleteNote","noteId":"note-1"}',
      confirmed: true,
    });
    expect(loadData).toHaveBeenCalled();
    expect(apiMocks.saveChatMessages).toHaveBeenCalledWith('确认执行', 'Action handled');

    await act(async () => {
      vi.advanceTimersByTime(600);
    });
    expect(useAiStore.getState().messages.at(-1)?.content).toBe('Action handled');
  });

  it('renders workflow card state transitions and retry actions', () => {
    const workflow = useWorkflowStore.getState();
    const cardId = workflow.createCard('delete', [
      { noteId: 'note-1', title: 'Alpha', folder: 'Projects' },
      { noteId: 'note-2', title: 'Beta', folder: 'Inbox' },
    ]);
    workflow.setThinkingSteps(cardId, [
      { step: 'scan', detail: 'Checking notes', done: true },
      { step: 'confirm', detail: 'Waiting for confirmation', done: false },
    ]);

    const onExecute = vi.fn();
    const onRetryExec = vi.fn();
    const { container } = render(
      <AiWorkflowCard cardId={cardId} onExecute={onExecute} onRetryExec={onRetryExec} />,
    );

    expect(screen.getByText('Checking notes')).toBeInTheDocument();

    act(() => {
      useWorkflowStore.getState().toConfirmation(cardId);
    });
    expect(screen.getByText('Alpha')).toBeInTheDocument();
    fireEvent.click(screen.getByText('Beta'));
    fireEvent.click(Array.from(container.querySelectorAll('button')).at(-1) as HTMLElement);
    expect(onExecute).toHaveBeenCalledWith(cardId);

    act(() => {
      const store = useWorkflowStore.getState();
      store.confirm(cardId);
      store.updateItemStatus(cardId, 'note-1', 'success');
      store.updateItemStatus(cardId, 'note-2', 'failed', 'permission denied');
      store.updateProgress(cardId, 2);
      store.complete(cardId, { successCount: 1, failedCount: 1 });
    });

    expect(screen.getByText('permission denied')).toBeInTheDocument();
    fireEvent.click(Array.from(container.querySelectorAll('button')).at(-1) as HTMLElement);
    expect(onRetryExec).toHaveBeenCalledWith(cardId);
  });
});
