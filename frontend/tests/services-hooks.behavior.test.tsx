import { act, render, renderHook, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useRef } from 'react';

const apiMocks = vi.hoisted(() => ({
  getNotes: vi.fn(),
  getFolders: vi.fn(),
}));

vi.mock('../src/api', () => ({
  getNotes: apiMocks.getNotes,
  getFolders: apiMocks.getFolders,
}));

import { API_BASE_URL, clearAuthStorage } from '../src/services/apiBase';
import { loadNotesAndFolders } from '../src/services/noteData';
import { resolveNoteId } from '../src/utils/notes';
import { useMeasuredHeight } from '../src/hooks/useMeasuredHeight';

describe('services and small utilities behavior', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('apiBase reads and clears auth storage', () => {
    localStorage.setItem('token', 'jwt-token');
    localStorage.setItem('auth-storage', 'persisted');

    expect(API_BASE_URL).toBeTruthy();
    clearAuthStorage();

    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('auth-storage')).toBeNull();
  });

  it('noteData loads notes and folders concurrently', async () => {
    apiMocks.getNotes.mockResolvedValueOnce([{ id: 'note-1' }]);
    apiMocks.getFolders.mockResolvedValueOnce([{ id: 'folder-1' }]);

    await expect(loadNotesAndFolders()).resolves.toEqual({
      notes: [{ id: 'note-1' }],
      folders: [{ id: 'folder-1' }],
    });
  });

  it('resolveNoteId prefers exact id and falls back to title', () => {
    const notes = [
      { id: 'note-1', title: 'Alpha' },
      { id: 'note-2', title: 'Beta' },
    ] as any[];

    expect(resolveNoteId(notes, 'note-1')).toBe('note-1');
    expect(resolveNoteId(notes, 'Beta')).toBe('note-2');
    expect(resolveNoteId(notes, 'Missing')).toBeUndefined();
  });

  it('useMeasuredHeight updates from ResizeObserver callbacks', async () => {
    let resizeCallback: ResizeObserverCallback | undefined;
    class ResizeObserverMock {
      constructor(callback: ResizeObserverCallback) {
        resizeCallback = callback;
      }
      observe() {}
      unobserve() {}
      disconnect() {}
    }
    vi.stubGlobal('ResizeObserver', ResizeObserverMock);
    let measuredHeight = 123;
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(() => ({
      width: 10,
      height: measuredHeight,
      top: 0,
      left: 0,
      right: 10,
      bottom: measuredHeight,
      x: 0,
      y: 0,
      toJSON: () => {},
    } as DOMRect));

    function Probe() {
      const ref = useRef<HTMLDivElement>(null);
      const height = useMeasuredHeight(ref, 50);
      return <div ref={ref}>height:{height}</div>;
    }

    render(<Probe />);

    expect(await screen.findByText('height:123')).toBeInTheDocument();

    measuredHeight = 180;
    await act(async () => {
      resizeCallback?.([], {} as ResizeObserver);
    });

    expect(await screen.findByText('height:180')).toBeInTheDocument();
  });
});
