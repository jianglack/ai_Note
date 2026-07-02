import { act, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Editor } from '@tiptap/react';

vi.mock('tippy.js', () => {
  const tippy = vi.fn(() => ({
    destroy: vi.fn(),
    hide: vi.fn(),
    setProps: vi.fn(),
    show: vi.fn(),
  }));
  return { default: tippy, tippy };
});

vi.mock('@tiptap/react', async () => {
  const actual = await vi.importActual<typeof import('@tiptap/react')>('@tiptap/react');
  const React = await import('react');
  return {
    ...actual,
    BubbleMenu: ({ children }: { children: unknown }) =>
      React.createElement(React.Fragment, null, children),
  };
});

import TiptapEditor from '../src/components/TiptapEditor';

class TestResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}

describe('TiptapEditor real behavior', () => {
  beforeEach(() => {
    vi.stubGlobal('ResizeObserver', TestResizeObserver);
    Element.prototype.scrollTo = vi.fn();
    Element.prototype.scrollIntoView = vi.fn();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('renders markdown through the real editor and syncs external content changes', async () => {
    const onChange = vi.fn();
    const { rerender } = render(
      <TiptapEditor
        content={'# Launch Plan\n\nInitial paragraph'}
        onChange={onChange}
        hideToolbar
      />,
    );

    expect(await screen.findByText('Launch Plan')).toBeInTheDocument();
    expect(screen.getByText('Initial paragraph')).toBeInTheDocument();
    expect(document.querySelector('.ProseMirror')).toBeTruthy();

    rerender(
      <TiptapEditor
        content={'## Updated Plan\n\nExternal content'}
        onChange={onChange}
        hideToolbar
      />,
    );

    expect(await screen.findByText('Updated Plan')).toBeInTheDocument();
    expect(screen.getByText('External content')).toBeInTheDocument();
  });

  it('debounces editor updates before emitting markdown changes', async () => {
    const onChange = vi.fn();
    let editor: Editor | null = null;

    render(
      <TiptapEditor
        content="Initial draft"
        onChange={onChange}
        onEditorReady={(instance) => {
          editor = instance;
        }}
        hideToolbar
      />,
    );

    await waitFor(() => expect(editor).not.toBeNull());
    onChange.mockClear();
    vi.useFakeTimers();

    act(() => {
      editor?.commands.setContent('<p>Edited body</p>', true);
    });
    expect(onChange).not.toHaveBeenCalled();

    act(() => {
      vi.advanceTimersByTime(399);
    });
    expect(onChange).not.toHaveBeenCalled();

    act(() => {
      vi.advanceTimersByTime(1);
    });

    expect(onChange).toHaveBeenCalled();
    expect(onChange.mock.calls.at(-1)?.[0]).toContain('Edited body');
  });
});
