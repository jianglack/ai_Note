import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { CSSProperties } from 'react';

const apiMocks = vi.hoisted(() => ({
  getNotes: vi.fn(),
  getFolders: vi.fn(),
  createNote: vi.fn(),
  deleteNote: vi.fn(),
  createFolder: vi.fn(),
  updateFolder: vi.fn(),
  deleteFolder: vi.fn(),
  getTrashNotes: vi.fn(),
  restoreNote: vi.fn(),
  permanentDeleteNote: vi.fn(),
  hybridSearch: vi.fn(),
  getCanvases: vi.fn(),
  createCanvas: vi.fn(),
  updateCanvas: vi.fn(),
  deleteCanvas: vi.fn(),
  generateCanvasAI: vi.fn(),
}));

const dialogMocks = vi.hoisted(() => ({
  askConfirm: vi.fn(),
  askPrompt: vi.fn(),
  showAlert: vi.fn(),
}));

vi.mock('../src/api', () => ({ ...apiMocks }));

vi.mock('../src/services/dialogService', () => ({
  askConfirm: dialogMocks.askConfirm,
  askPrompt: dialogMocks.askPrompt,
  showAlert: dialogMocks.showAlert,
}));

vi.mock('react-window', async () => {
  const React = await vi.importActual<typeof import('react')>('react');
  function renderListRows(props: any) {
    const RowComponent = props.rowComponent;
    return Array.from({ length: props.rowCount }).map((_, index) => {
      const rowHeight = typeof props.rowHeight === 'function'
        ? props.rowHeight(index, props.rowProps)
        : props.rowHeight;
      return (
        <RowComponent
          key={index}
          ariaAttributes={{ 'aria-posinset': index + 1, 'aria-setsize': props.rowCount, role: 'listitem' }}
          index={index}
          style={{ height: rowHeight } as CSSProperties}
          {...props.rowProps}
        />
      );
    });
  }

  const FixedSizeList = (props: any) => (
    <div className={props.className}>
      {Array.from({ length: props.itemCount }).map((_, index) => props.children({
        index,
        style: { height: props.itemSize } as CSSProperties,
      }))}
    </div>
  );
  const List = (props: any) => {
    React.useImperativeHandle(props.listRef, () => ({ scrollToRow: vi.fn() }));
    return <div className={props.className}>{renderListRows(props)}</div>;
  };

  return { FixedSizeList, List };
});

import type { Folder, Note } from '../src/api';
import Sidebar from '../src/components/Sidebar';
import CanvasView from '../src/components/features/CanvasView';
import { useAuthStore } from '../src/stores/authStore';
import { useNoteStore } from '../src/stores/noteStore';
import { useScheduleStore } from '../src/stores/scheduleStore';
import { useUiStore } from '../src/stores/uiStore';

function makeNote(overrides: Partial<Note> = {}): Note {
  return {
    id: 'note-1',
    title: 'Alpha',
    content: '<p>Body</p>',
    folderId: null,
    createdAt: '2026-06-26T10:00:00Z',
    updatedAt: '2026-06-26T10:15:00Z',
    tags: [],
    ...overrides,
  };
}

function makeFolder(overrides: Partial<Folder> = {}): Folder {
  return {
    id: 'folder-1',
    name: 'Projects',
    color: '#b8452e',
    parentId: null,
    createdAt: '2026-06-26T09:00:00Z',
    updatedAt: '2026-06-26T09:00:00Z',
    ...overrides,
  };
}

describe('sidebar and canvas behavior', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
    useAuthStore.setState({
      user: { userId: 'user-1', username: 'alice', email: 'alice@example.com' },
      isAuthenticated: true,
      initialized: true,
    });
    useNoteStore.setState({
      notes: [makeNote()],
      folders: [makeFolder()],
      selectedNote: null,
      trashNotes: [],
    });
    useUiStore.setState({
      searchQuery: '',
      searchResults: [],
      isSearching: false,
      isGraphOpen: false,
      isTimelineOpen: false,
      isCanvasOpen: false,
      viewMode: 'notes',
      sidebarFilter: 'all',
      selectedFolderId: null,
    });
    useScheduleStore.setState({ schedules: [], editingSchedule: undefined });
    globalThis.ResizeObserver = class ResizeObserver {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(() => ({
      width: 640,
      height: 420,
      top: 0,
      left: 0,
      right: 640,
      bottom: 420,
      x: 0,
      y: 0,
      toJSON: () => {},
    } as DOMRect));
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('Sidebar debounces search requests and aborts stale searches', () => {
    vi.useFakeTimers();
    apiMocks.hybridSearch.mockImplementation(() => new Promise(() => {}));

    render(<Sidebar onLogout={vi.fn()} />);

    const input = screen.getByRole('textbox');
    fireEvent.change(input, { target: { value: 'alpha' } });

    act(() => {
      vi.advanceTimersByTime(299);
    });
    expect(apiMocks.hybridSearch).not.toHaveBeenCalled();

    act(() => {
      vi.advanceTimersByTime(1);
    });
    expect(apiMocks.hybridSearch).toHaveBeenCalledTimes(1);
    const firstSignal = apiMocks.hybridSearch.mock.calls[0][1] as AbortSignal;
    expect(firstSignal.aborted).toBe(false);

    fireEvent.change(input, { target: { value: 'beta' } });
    expect(firstSignal.aborted).toBe(true);

    act(() => {
      vi.advanceTimersByTime(300);
    });
    expect(apiMocks.hybridSearch).toHaveBeenCalledTimes(2);
    expect(apiMocks.hybridSearch.mock.calls[1][0]).toBe('beta');

    fireEvent.click(screen.getByRole('button', { name: 'Open graph view' }));
    expect(useUiStore.getState().isGraphOpen).toBe(true);
  });

  it('CanvasView saves dragged node coordinates', async () => {
    const user = userEvent.setup();
    apiMocks.getCanvases.mockResolvedValueOnce([{
      id: 'canvas-1',
      title: 'Board',
      data: JSON.stringify({
        nodes: [{
          id: 'n1',
          x: 100,
          y: 100,
          w: 220,
          h: 130,
          color: '#b8452e',
          title: 'Node A',
          preview: 'Preview',
          tags: [],
        }],
        edges: [],
      }),
      createdAt: '2026-06-26T09:00:00Z',
      updatedAt: '2026-06-26T09:30:00Z',
    }]);
    apiMocks.updateCanvas.mockResolvedValueOnce({});

    const { container } = render(<CanvasView onClose={vi.fn()} />);

    expect(screen.getByRole('dialog', { name: '画布' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /新建画布/ })).toBeInTheDocument();

    await screen.findByText('Board');
    await user.click(screen.getByText('Board'));

    const nodeTitle = screen.getAllByText('Node A')[0];
    fireEvent.mouseDown(nodeTitle, { clientX: 10, clientY: 10 });
    fireEvent.mouseMove(window, { clientX: 60, clientY: 40 });
    fireEvent.mouseUp(window);

    await user.click(container.querySelector('.cv-top .feat-btn.small.ghost')!);

    await waitFor(() => expect(apiMocks.updateCanvas).toHaveBeenCalledTimes(1));
    const savedData = JSON.parse(apiMocks.updateCanvas.mock.calls[0][2]);

    expect(apiMocks.updateCanvas.mock.calls[0][0]).toBe('canvas-1');
    expect(savedData.nodes[0]).toEqual(expect.objectContaining({
      id: 'n1',
      x: 150,
      y: 130,
    }));
  });
});
