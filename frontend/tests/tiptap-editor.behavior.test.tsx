import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const tiptapState = vi.hoisted(() => ({
  html: '',
  options: null as any,
  editor: null as any,
  setContent: vi.fn((html: string) => {
    tiptapState.html = html;
  }),
}));

function configurableExtension() {
  return { configure: vi.fn(() => ({})) };
}

vi.mock('@tiptap/starter-kit', () => ({ default: configurableExtension() }));
vi.mock('@tiptap/extension-placeholder', () => ({ default: configurableExtension() }));
vi.mock('@tiptap/extension-link', () => ({ default: configurableExtension() }));
vi.mock('@tiptap/extension-image', () => ({ default: configurableExtension() }));
vi.mock('@tiptap/extension-table', () => ({ default: configurableExtension() }));
vi.mock('@tiptap/extension-table-row', () => ({ default: {} }));
vi.mock('@tiptap/extension-table-cell', () => ({ default: {} }));
vi.mock('@tiptap/extension-table-header', () => ({ default: {} }));
vi.mock('@tiptap/extension-task-list', () => ({ default: {} }));
vi.mock('@tiptap/extension-task-item', () => ({ default: configurableExtension() }));
vi.mock('@tiptap/extension-underline', () => ({ default: {} }));
vi.mock('@tiptap/extension-highlight', () => ({ default: configurableExtension() }));
vi.mock('@tiptap/extension-superscript', () => ({ default: {} }));
vi.mock('@tiptap/extension-subscript', () => ({ default: {} }));
vi.mock('@tiptap/extension-code-block-lowlight', () => ({ default: configurableExtension() }));
vi.mock('@tiptap/extension-mathematics', () => ({ default: configurableExtension() }));
vi.mock('@tiptap/extension-text-style', () => ({ default: {} }));
vi.mock('@tiptap/extension-color', () => ({ default: {} }));
vi.mock('lowlight', () => ({ createLowlight: vi.fn(() => ({})), common: {} }));
vi.mock('marked', () => ({
  marked: {
    use: vi.fn(),
    parse: vi.fn((text: string) => `<p>${text}</p>`),
  },
}));
vi.mock('turndown', () => ({
  default: class TurndownService {
    addRule() {}
    turndown(html: string) {
      return html.replace(/<[^>]+>/g, '').trim();
    }
  },
}));

vi.mock('../src/components/WikiLink', () => ({ default: {} }));
vi.mock('../src/components/Annotation', () => ({ Annotation: {} }));
vi.mock('../src/components/SlashCommand', () => ({ default: configurableExtension() }));
vi.mock('../src/components/slashSuggestion', () => ({
  createSlashSuggestion: vi.fn(() => ({})),
}));
vi.mock('../src/api', () => ({
  getAnnotations: vi.fn(),
  createAnnotation: vi.fn(),
  updateAnnotation: vi.fn(),
  deleteAnnotation: vi.fn(),
  uploadImage: vi.fn(),
}));
vi.mock('../src/security/contentSafety', () => ({
  isSafeAnchorUrl: vi.fn(() => true),
  sanitizeAnchorUrl: vi.fn((url: string) => url),
  sanitizeFormulaInput: vi.fn((formula: string) => formula),
  sanitizeImageUrl: vi.fn((url: string) => url),
}));
vi.mock('../src/services/apiBase', () => ({
  API_BASE_URL: 'http://backend.test',
}));
vi.mock('../src/services/dialogService', () => ({
  askConfirm: vi.fn(),
  askPrompt: vi.fn(),
  showAlert: vi.fn(),
}));

vi.mock('@tiptap/react', async () => {
  const React = await vi.importActual<typeof import('react')>('react');

  function makeChain() {
    const chain = new Proxy(
      { run: vi.fn(() => true) },
      {
        get(target, property) {
          if (property in target) return (target as any)[property];
          return vi.fn(() => chain);
        },
      },
    );
    return chain;
  }

  const editor = {
    getHTML: () => tiptapState.html,
    commands: {
      setContent: tiptapState.setContent,
    },
    isActive: vi.fn(() => false),
    getAttributes: vi.fn(() => ({})),
    chain: vi.fn(() => makeChain()),
    can: vi.fn(() => ({ chain: () => makeChain() })),
    state: {
      selection: { from: 0, to: 0 },
      doc: { textBetween: vi.fn(() => '') },
    },
    view: {
      coordsAtPos: vi.fn(() => ({ top: 0, left: 0 })),
      dom: document.createElement('div'),
    },
  };
  tiptapState.editor = editor;

  return {
    useEditor: vi.fn((options: any) => {
      tiptapState.options = options;
      return editor;
    }),
    EditorContent: ({ editor }: any) => (
      <textarea
        aria-label="mock editor"
        defaultValue={tiptapState.html}
        onChange={(event) => {
          tiptapState.html = event.currentTarget.value;
          tiptapState.options?.onUpdate?.({ editor });
        }}
      />
    ),
    BubbleMenu: ({ children }: any) => <div>{children}</div>,
  };
});

import TiptapEditor from '../src/components/TiptapEditor';

describe('TiptapEditor behavior', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
    tiptapState.html = '';
    tiptapState.options = null;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('debounces editor updates and syncs external content into TipTap', async () => {
    const onChange = vi.fn();
    const onEditorReady = vi.fn();
    const { rerender } = render(
      <TiptapEditor
        content="Initial"
        onChange={onChange}
        onEditorReady={onEditorReady}
        hideToolbar
      />,
    );

    await act(async () => {
      await Promise.resolve();
    });

    expect(tiptapState.setContent).toHaveBeenCalledWith('Initial');
    expect(onEditorReady).toHaveBeenCalledWith(tiptapState.editor);

    fireEvent.change(screen.getByLabelText('mock editor'), {
      target: { value: '<p>Edited</p>' },
    });

    act(() => {
      vi.advanceTimersByTime(399);
    });
    expect(onChange).not.toHaveBeenCalled();

    act(() => {
      vi.advanceTimersByTime(1);
    });
    expect(onChange).toHaveBeenCalledWith('Edited');

    rerender(
      <TiptapEditor
        content="Reloaded"
        onChange={onChange}
        onEditorReady={onEditorReady}
        hideToolbar
      />,
    );
    await act(async () => {
      await Promise.resolve();
    });

    expect(tiptapState.setContent).toHaveBeenCalledWith('Reloaded');
  });
});
