import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { CSSProperties } from 'react';

const apiMocks = vi.hoisted(() => ({
  createFolder: vi.fn(),
  getNotes: vi.fn(),
  getFolders: vi.fn(),
  updateFolder: vi.fn(),
  deleteFolder: vi.fn(),
  getTrashNotes: vi.fn(),
  restoreNote: vi.fn(),
  permanentDeleteNote: vi.fn(),
  hybridSearch: vi.fn(),
  getTypedLinksByNote: vi.fn(),
  createTypedLink: vi.fn(),
  deleteTypedLink: vi.fn(),
  searchNotes: vi.fn(),
  updateScheduleStatus: vi.fn(),
}));

const dialogMocks = vi.hoisted(() => ({
  askConfirm: vi.fn(),
  askPrompt: vi.fn(),
}));

vi.mock('../src/api', () => ({
  ...apiMocks,
}));

vi.mock('../src/services/dialogService', () => ({
  askConfirm: dialogMocks.askConfirm,
  askPrompt: dialogMocks.askPrompt,
}));

vi.mock('react-window', async () => {
  const React = await vi.importActual<typeof import('react')>('react');
  const FixedSizeList = (props: any) => (
    <div className={props.className}>
      {Array.from({ length: props.itemCount }).map((_, index) => props.children({
        index,
        style: { height: props.itemSize } as CSSProperties,
      }))}
    </div>
  );
  return { FixedSizeList };
});

import Sidebar from '../src/components/Sidebar';
import AiInlineSuggestion from '../src/components/AiInlineSuggestion';
import EmptyState from '../src/components/EmptyState';
import ScheduleCard from '../src/components/ScheduleCard';
import LinksPanel from '../src/components/features/LinksPanel';
import {
  isSafeAnchorUrl,
  isSafeImageUrl,
  markdownUrlTransform,
  sanitizeAnchorUrl,
  sanitizeEditorHtml,
  sanitizeFormulaInput,
  sanitizeHtmlFallback,
  sanitizeImageUrl,
  shouldShowErrorStack,
} from '../src/security/contentSafety';
import type { Folder, Note, Schedule } from '../src/api';
import { useAuthStore } from '../src/stores/authStore';
import { useNoteStore } from '../src/stores/noteStore';
import { useScheduleStore } from '../src/stores/scheduleStore';
import { useUiStore } from '../src/stores/uiStore';
import { useWorkflowStore } from '../src/stores/workflowStore';

function note(overrides: Partial<Note> = {}): Note {
  return {
    id: 'note-1',
    title: 'Alpha',
    content: '<p>Alpha body</p>',
    folderId: 'folder-1',
    createdAt: '2026-06-27T01:00:00Z',
    updatedAt: new Date().toISOString(),
    pinned: false,
    starred: true,
    tags: [{ id: 'tag-1', name: 'planning' }],
    ...overrides,
  };
}

function folder(overrides: Partial<Folder> = {}): Folder {
  return {
    id: 'folder-1',
    name: 'Projects',
    color: '#b8452e',
    parentId: null,
    createdAt: '2026-06-27T00:00:00Z',
    updatedAt: '2026-06-27T00:00:00Z',
    ...overrides,
  };
}

describe('coverage depth behavior', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    useAuthStore.setState({
      user: { userId: 'user-1', username: 'alice', email: 'alice@example.com' },
      token: 'token',
      isAuthenticated: true,
    });
    useNoteStore.setState({
      notes: [
        note(),
        note({
          id: 'note-2',
          title: 'Beta',
          content: '<p>Beta body</p>',
          folderId: null,
          starred: false,
          tags: [{ id: 'tag-2', name: 'review' }],
        }),
      ],
      folders: [folder()],
      selectedNote: note(),
      trashNotes: [],
    });
    useScheduleStore.setState({ schedules: [], editingSchedule: undefined });
    useUiStore.setState({
      sidebarFilter: 'all',
      selectedFolderId: null,
      searchQuery: '',
      searchResults: [],
      isSearching: false,
      isGraphOpen: false,
      isTimelineOpen: false,
      isCanvasOpen: false,
      isScheduleFormOpen: false,
      isWorkflowsOpen: false,
      isTaskPanelOpen: false,
      isTaskScheduleOpen: false,
      isEvalDashOpen: false,
      isAgentMetricsOpen: false,
      isTracesPanelOpen: false,
      isCostDashboardOpen: false,
      isSettingsOpen: false,
      viewMode: 'notes',
    });
    apiMocks.updateFolder.mockImplementation((id: string, name: string, color: string | null, parentId: string | null) => Promise.resolve(folder({
      id,
      name,
      color,
      parentId,
    })));
    apiMocks.deleteFolder.mockResolvedValue(undefined);
    apiMocks.getTrashNotes.mockResolvedValue([]);
    dialogMocks.askConfirm.mockResolvedValue(true);
    dialogMocks.askPrompt.mockResolvedValue('New folder');
    globalThis.ResizeObserver = class ResizeObserver {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
  });

  it('exercises content safety URL and HTML branches', () => {
    expect(isSafeAnchorUrl('https://example.test/doc')).toBe(true);
    expect(isSafeAnchorUrl('/relative/doc')).toBe(true);
    expect(isSafeAnchorUrl('#heading')).toBe(true);
    expect(isSafeAnchorUrl('mailto:team@example.test')).toBe(true);
    expect(isSafeAnchorUrl('//evil.test/doc')).toBe(false);
    expect(isSafeAnchorUrl('java\nscript:alert(1)')).toBe(false);
    expect(sanitizeAnchorUrl('ftp://example.test')).toBeUndefined();

    expect(isSafeImageUrl('data:image/png;base64,AAAA')).toBe(true);
    expect(isSafeImageUrl('./asset.webp')).toBe(true);
    expect(isSafeImageUrl('data:text/html;base64,PHNjcmlwdA==')).toBe(false);
    expect(sanitizeImageUrl('file:///secret.png')).toBeUndefined();
    expect(markdownUrlTransform('javascript:alert(1)', 'href')).toBe('');
    expect(markdownUrlTransform('/image.png', 'src')).toBe('/image.png');
    expect(markdownUrlTransform('literal', 'title')).toBe('literal');

    expect(sanitizeFormulaInput(' x^2 ')).toBe('x^2');
    expect(sanitizeFormulaInput('<img>')).toBeNull();
    expect(sanitizeFormulaInput('   ')).toBeNull();

    const fallback = sanitizeHtmlFallback('<p onclick="x()"><a href="javascript:bad()">x</a><img src="/ok.png"><script>x</script></p>');
    expect(fallback).not.toContain('onclick');
    expect(fallback).not.toContain('javascript');
    expect(fallback).not.toContain('<script');
    expect(fallback).toContain('src="/ok.png"');

    const sanitized = sanitizeEditorHtml('<svg></svg><p style="color:red"><a href="/safe">safe</a></p>');
    expect(sanitized).not.toContain('<svg');
    expect(sanitized).not.toContain('style=');
    expect(sanitized).toContain('/safe');
    expect(shouldShowErrorStack('development')).toBe(true);
    expect(shouldShowErrorStack('production')).toBe(false);
  });

  it('drives sidebar filters, folder actions, tools, and trash loading', async () => {
    const user = userEvent.setup();
    const onLogout = vi.fn();

    render(<Sidebar onLogout={onLogout} />);

    await user.click(screen.getByRole('button', { name: 'Open folder Projects' }));
    expect(useUiStore.getState().selectedFolderId).toBe('folder-1');

    await user.click(screen.getByRole('button', { name: 'Open unfiled notes' }));
    expect(useUiStore.getState().selectedFolderId).toBe('__unfiled__');

    await user.click(screen.getByRole('button', { name: 'Open timeline view' }));
    await user.click(screen.getByRole('button', { name: 'Open canvas' }));
    await user.click(screen.getByRole('button', { name: 'Create schedule' }));
    await user.click(screen.getByRole('button', { name: 'Open workflows' }));
    await user.click(screen.getByRole('button', { name: 'Open task center' }));
    await user.click(screen.getByRole('button', { name: 'Open scheduled tasks' }));
    await user.click(screen.getByRole('button', { name: 'Open evaluation dashboard' }));
    await user.click(screen.getByRole('button', { name: 'Open agent metrics' }));
    await user.click(screen.getByRole('button', { name: 'Open trace panel' }));
    await user.click(screen.getByRole('button', { name: 'Open cost dashboard' }));
    await user.click(screen.getByRole('button', { name: 'Open settings' }));

    expect(useUiStore.getState()).toMatchObject({
      isTimelineOpen: true,
      isCanvasOpen: true,
      isScheduleFormOpen: true,
      isWorkflowsOpen: true,
      isTaskPanelOpen: true,
      isTaskScheduleOpen: true,
      isEvalDashOpen: true,
      isAgentMetricsOpen: true,
      isTracesPanelOpen: true,
      isCostDashboardOpen: true,
      isSettingsOpen: true,
    });

    await user.click(screen.getByRole('button', { name: 'Open trash' }));
    await waitFor(() => expect(apiMocks.getTrashNotes).toHaveBeenCalledTimes(1));
    expect(useUiStore.getState().viewMode).toBe('trash');

    await user.click(screen.getByRole('button', { name: 'More actions for Projects' }));
    const menu = await screen.findByRole('menu', { name: /Projects/ });
    await user.click(within(menu).getAllByRole('menuitem', { name: /Set folder color/ })[1]);
    await waitFor(() => expect(apiMocks.updateFolder).toHaveBeenCalledWith('folder-1', 'Projects', expect.any(String), null));

    await user.click(screen.getByRole('button', { name: 'More actions for Projects' }));
    await user.click(await screen.findByText(/删除/));
    await waitFor(() => expect(apiMocks.deleteFolder).toHaveBeenCalledWith('folder-1'));

    await user.click(screen.getByRole('button', { name: 'Log out' }));
    expect(onLogout).toHaveBeenCalledTimes(1);
  }, 20_000);

  it('adds and deletes typed links from the selected note', async () => {
    const user = userEvent.setup();
    apiMocks.getTypedLinksByNote.mockResolvedValueOnce([{
      id: 'link-1',
      sourceNoteId: 'note-1',
      targetNoteId: 'note-2',
      relationType: 'supports',
      context: 'Existing context',
    }]).mockResolvedValueOnce([]);
    apiMocks.searchNotes.mockResolvedValue([note({ id: 'note-2', title: 'Beta' })]);
    apiMocks.createTypedLink.mockResolvedValue({ id: 'link-2' });
    apiMocks.deleteTypedLink.mockResolvedValue(undefined);

    render(<LinksPanel />);

    await screen.findByText('Beta');
    await user.click(screen.getByText('×'));
    await waitFor(() => expect(apiMocks.deleteTypedLink).toHaveBeenCalledWith('link-1'));

    await user.click(screen.getByRole('button', { name: '+' }));
    const modal = screen.getByText('添加关系链接').closest('.lp-modal') as HTMLElement;
    const inputs = within(modal).getAllByRole('textbox');
    fireEvent.change(inputs[0], { target: { value: 'Bet' } });
    await user.click(await screen.findByText('Beta'));
    await user.click(screen.getByText(/目标.*当前笔记/));
    fireEvent.change(inputs[1], { target: { value: 'New context' } });
    await user.click(screen.getByRole('button', { name: /添加链接/ }));

    await waitFor(() => expect(apiMocks.createTypedLink).toHaveBeenCalledWith('note-2', 'note-1', 'supports', 'New context'));
  });

  it('covers small action components and schedule status transitions', async () => {
    const user = userEvent.setup();
    const onCreateNote = vi.fn();
    const onAiStart = vi.fn();
    const onImport = vi.fn();
    const onLink = vi.fn();
    const onDismiss = vi.fn();

    const { rerender } = render(
      <EmptyState onCreateNote={onCreateNote} onAiStart={onAiStart} onImport={onImport} />,
    );

    await user.click(screen.getByRole('button', { name: 'Create blank note' }));
    await user.click(screen.getByRole('button', { name: 'Start with AI' }));
    await user.click(screen.getByRole('button', { name: 'Import notes' }));
    expect(onCreateNote).toHaveBeenCalledTimes(1);
    expect(onAiStart).toHaveBeenCalledTimes(1);
    expect(onImport).toHaveBeenCalledTimes(1);

    rerender(
      <AiInlineSuggestion
        noteTitle="Beta"
        noteDate="2026-06-27"
        description="Related to "
        onLink={onLink}
        onDismiss={onDismiss}
      />,
    );
    await user.click(screen.getByRole('button', { name: /建立关联/ }));
    await user.click(screen.getByRole('button', { name: /忽略/ }));
    expect(onLink).toHaveBeenCalledTimes(1);
    expect(onDismiss).toHaveBeenCalledTimes(1);

    const schedule: Schedule = {
      id: 'schedule-1',
      title: 'Weekly review',
      description: 'Review project',
      startTime: '2026-06-27T10:00:00Z',
      endTime: '2026-06-27T11:00:00Z',
      allDay: false,
      rrule: 'FREQ=WEEKLY',
      reminderMinutes: 10,
      status: 'pending',
      notes: [{ id: 'note-1', title: 'Alpha' }],
      createdAt: '2026-06-27T09:00:00Z',
      updatedAt: '2026-06-27T09:00:00Z',
    };
    const completed = { ...schedule, status: 'completed' as const };
    const onSelect = vi.fn();
    const onStatusChange = vi.fn();
    apiMocks.updateScheduleStatus.mockResolvedValueOnce(completed);

    rerender(<ScheduleCard schedule={schedule} onSelect={onSelect} onStatusChange={onStatusChange} />);
    await user.click(screen.getByText('Weekly review'));
    expect(onSelect).toHaveBeenCalledWith(schedule);

    await user.click(document.querySelector('.schedule-checkbox') as HTMLElement);
    await waitFor(() => expect(apiMocks.updateScheduleStatus).toHaveBeenCalledWith('schedule-1', 'completed'));
    expect(onStatusChange).toHaveBeenCalledWith(completed);

    rerender(<ScheduleCard schedule={completed} onSelect={onSelect} onStatusChange={onStatusChange} />);
    await user.click(document.querySelector('.schedule-checkbox') as HTMLElement);
    expect(apiMocks.updateScheduleStatus).toHaveBeenCalledTimes(1);
  });

  it('covers uiStore modal and selection state transitions', () => {
    const sourceNote = note({ id: 'source-note', title: 'Source' });
    const candidate = note({ id: 'candidate-note', title: 'Candidate' });
    const ui = useUiStore.getState();

    ui.setIsQuickSwitcherOpen(true);
    ui.setIsExtractDialogOpen(true);
    ui.setIsMindMapOpen(true);
    ui.setIsAiChatOpen(false);
    ui.setIsLinksPanelOpen(true);
    ui.setPendingActions([{ id: 'pending-1', type: 'DELETE_NOTE', description: 'Delete note', data: { noteId: 'n1' } }]);
    ui.setShowConfirmDialog(true);
    ui.setShowNoteSelectionDialog(true);
    ui.setNoteSelectionContext({ operation: 'delete', message: 'Select notes', candidates: [candidate] });
    ui.setSelectedNoteIds(new Set(['candidate-note']));
    ui.toggleNoteSelection('candidate-note');
    ui.toggleNoteSelection('another-note');

    ui.setShowClassificationDialog(true);
    ui.setClassificationLoading(true);
    ui.setClassificationResult({ suggestions: [], newFolders: [], summary: 'No changes' });
    ui.setSelectedClassifications(new Set(['candidate-note']));
    ui.toggleClassification('candidate-note');
    ui.toggleClassification('another-note');

    ui.setShowSplitDialog(true);
    ui.setSplitSourceNote(sourceNote);
    ui.setDeleteAfterSplit(true);
    ui.setSplitPreviews([
      { title: 'Part one', content: 'A', selected: true },
      { title: 'Part two', content: 'B', selected: false },
    ]);
    ui.toggleSplitItem(0);

    ui.setShowExportDialog(true);
    ui.setExportCandidates([candidate]);
    ui.setSelectedExportIds(new Set(['candidate-note']));
    ui.toggleExportItem('candidate-note');
    ui.toggleExportItem('another-note');

    ui.setMultiSelectMode(true);
    ui.toggleMultiNoteSelection('candidate-note');
    ui.toggleMultiNoteSelection('candidate-note');
    ui.selectAllMultiNotes(['a', 'b']);
    ui.clearMultiSelection();
    ui.toggleMultiSelect();

    ui.setShowSummaryDialog(true);
    ui.setSummaryContent('Summary');
    ui.setSummaryTargetNote(sourceNote);
    ui.setSummaryLoading(true);

    const state = useUiStore.getState();
    expect(state.isQuickSwitcherOpen).toBe(true);
    expect(state.isExtractDialogOpen).toBe(true);
    expect(state.isMindMapOpen).toBe(true);
    expect(state.isAiChatOpen).toBe(false);
    expect(state.isLinksPanelOpen).toBe(true);
    expect(state.pendingActions).toHaveLength(1);
    expect(state.showConfirmDialog).toBe(true);
    expect(state.showNoteSelectionDialog).toBe(true);
    expect(state.selectedNoteIds.has('another-note')).toBe(true);
    expect(state.showClassificationDialog).toBe(true);
    expect(state.classificationLoading).toBe(true);
    expect(state.selectedClassifications.has('another-note')).toBe(true);
    expect(state.showSplitDialog).toBe(true);
    expect(state.splitPreviews[0].selected).toBe(false);
    expect(state.deleteAfterSplit).toBe(true);
    expect(state.showExportDialog).toBe(true);
    expect(state.selectedExportIds.has('another-note')).toBe(true);
    expect(state.multiSelectedNoteIds.size).toBe(0);
    expect(state.summaryContent).toBe('Summary');
    expect(state.summaryTargetNote).toBe(sourceNote);
    expect(state.summaryLoading).toBe(true);
  });

  it('covers noteStore list reconciliation and async loading', async () => {
    const alpha = note({ id: 'alpha', title: 'Alpha', folderId: 'folder-1' });
    const beta = note({ id: 'beta', title: 'Beta', folderId: 'folder-1' });
    const loaded = note({ id: 'loaded', title: 'Loaded', folderId: null });
    const store = useNoteStore.getState();

    store.setSelectedNote(alpha);
    store.setNotes([alpha, beta]);
    expect(useNoteStore.getState().selectedNote?.id).toBe('alpha');

    store.setNotes([beta]);
    expect(useNoteStore.getState().selectedNote).toBeNull();

    store.addNote(alpha);
    store.setSelectedNote(alpha);
    store.updateNoteContent('alpha', '<p>Updated</p>');
    expect(useNoteStore.getState().selectedNote?.content).toBe('<p>Updated</p>');

    store.updateNoteInList({ ...alpha, title: 'Alpha updated' });
    expect(useNoteStore.getState().selectedNote?.title).toBe('Alpha updated');

    store.addFolder(folder({ id: 'folder-2', name: 'Archive' }));
    store.updateFolderInList(folder({ id: 'folder-2', name: 'Archive 2' }));
    expect(useNoteStore.getState().folders.some((item) => item.name === 'Archive 2')).toBe(true);

    store.moveNotesToRoot('folder-1');
    expect(useNoteStore.getState().notes.every((item) => item.folderId !== 'folder-1')).toBe(true);

    store.setTrashNotes([alpha, beta]);
    store.removeTrashNote('alpha');
    expect(useNoteStore.getState().trashNotes.map((item) => item.id)).toEqual(['beta']);

    store.removeFolder('folder-2');
    expect(useNoteStore.getState().folders.some((item) => item.id === 'folder-2')).toBe(false);

    store.removeNote('alpha');
    expect(useNoteStore.getState().notes.some((item) => item.id === 'alpha')).toBe(false);

    apiMocks.getNotes.mockResolvedValueOnce([loaded]);
    apiMocks.getFolders.mockResolvedValueOnce([folder({ id: 'loaded-folder', name: 'Loaded folder' })]);
    await useNoteStore.getState().loadData();
    expect(useNoteStore.getState().notes).toEqual([loaded]);
    expect(useNoteStore.getState().folders[0].id).toBe('loaded-folder');

    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    apiMocks.getNotes.mockRejectedValueOnce(new Error('boom'));
    apiMocks.getFolders.mockResolvedValueOnce([]);
    await useNoteStore.getState().loadData();
    expect(consoleSpy).toHaveBeenCalled();
    consoleSpy.mockRestore();
  });

  it('covers workflowStore terminal states and retry guards', () => {
    useWorkflowStore.setState({ cards: {}, activeCardId: null });
    const workflow = useWorkflowStore.getState();

    workflow.setThinkingSteps('missing', []);
    workflow.toConfirmation('missing');
    workflow.confirm('missing');
    workflow.updateProgress('missing', 1);
    workflow.complete('missing', { successCount: 0, failedCount: 0 });
    workflow.retryFailed('missing');
    expect(workflow.getCard('missing')).toBeUndefined();
    expect(workflow.getSelectedItems('missing')).toEqual([]);

    const doneId = workflow.createCard('archive', [{ noteId: 'a', title: 'A' }]);
    workflow.setThinkingSteps(doneId, [{ step: 'review', detail: 'Check input', done: false }]);
    workflow.toConfirmation(doneId);
    workflow.toggleAll(doneId);
    expect(workflow.getSelectedItems(doneId)).toEqual([]);
    workflow.toggleAll(doneId);
    workflow.confirm(doneId);
    workflow.updateProgress(doneId, 1);
    workflow.complete(doneId, { successCount: 1, failedCount: 0 });
    expect(workflow.getCard(doneId)?.state).toBe('done');

    const failedId = workflow.createCard('delete', [{ noteId: 'b', title: 'B' }]);
    workflow.toConfirmation(failedId);
    workflow.toggleItem(failedId, 'b');
    workflow.toggleItem(failedId, 'b');
    workflow.confirm(failedId);
    workflow.updateItemStatus(failedId, 'b', 'failed', 'boom');
    workflow.complete(failedId, { successCount: 0, failedCount: 1 });
    expect(workflow.getCard(failedId)?.state).toBe('failed');
    workflow.retryFailed(failedId);
    expect(workflow.getCard(failedId)).toMatchObject({
      state: 'executing',
      progress: { current: 0, total: 1 },
      result: undefined,
    });

    const cancelledId = workflow.createCard('move', [{ noteId: 'c', title: 'C' }], { folderId: 'folder-1' });
    workflow.cancel(cancelledId);
    expect(workflow.getCard(cancelledId)?.state).toBe('cancelled');
  });
});
