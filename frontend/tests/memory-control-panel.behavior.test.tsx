import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const apiMocks = vi.hoisted(() => ({
  deleteMemory: vi.fn(),
  exportMemories: vi.fn(),
  getMemoryEvents: vi.fn(),
  getMemories: vi.fn(),
  previewContext: vi.fn(),
  updateMemory: vi.fn(),
}));

const dialogMocks = vi.hoisted(() => ({
  askConfirm: vi.fn(),
}));

vi.mock('../src/api', () => ({
  deleteMemory: apiMocks.deleteMemory,
  exportMemories: apiMocks.exportMemories,
  getMemoryEvents: apiMocks.getMemoryEvents,
  getMemories: apiMocks.getMemories,
  previewContext: apiMocks.previewContext,
  updateMemory: apiMocks.updateMemory,
}));

vi.mock('../src/services/dialogService', () => ({
  askConfirm: dialogMocks.askConfirm,
}));

import MemoryControlPanel from '../src/components/MemoryControlPanel';
import { useToastStore } from '../src/stores/toastStore';
import type { MemoryRecord } from '../src/api';

function makeMemory(overrides: Partial<MemoryRecord> = {}): MemoryRecord {
  return {
    id: 1,
    type: 'semantic',
    memoryType: 'preference',
    category: 'style',
    content: '用户希望回答简洁、直接。',
    confidence: 0.92,
    source: 'chat',
    scope: 'global',
    status: 'active',
    sourceTraceId: 'trace-1',
    sourceMessageIds: 'msg-1',
    sourceToolCallId: null,
    evidenceExcerpt: '记住，我希望你回答简洁一点',
    lastAccessedAt: null,
    accessCount: 3,
    supersedesId: null,
    createdAt: '2026-07-03T09:00:00',
    updatedAt: '2026-07-03T09:30:00',
    ...overrides,
  };
}

describe('MemoryControlPanel behavior', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useToastStore.setState({ toasts: [] });
    apiMocks.getMemories.mockResolvedValue({ items: [makeMemory()], nextCursor: null });
    apiMocks.getMemoryEvents.mockResolvedValue({
      items: [
        {
          id: 101,
          memoryId: 1,
          eventType: 'CREATED',
          actor: 'assistant',
          reason: 'explicit_memory',
          beforeJson: null,
          afterJson: '{"content":"用户希望回答简洁、直接。"}',
          traceId: 'memory-capture-abc',
          createdAt: '2026-07-03T10:05:00',
        },
      ],
    });
    apiMocks.exportMemories.mockResolvedValue({ items: [makeMemory()], nextCursor: null });
    apiMocks.previewContext.mockResolvedValue({
      query: '总结这篇笔记',
      noteIds: [],
      selectedNoteCount: 0,
      intent: 'STANDARD',
      contextChars: 96,
      estimatedTokens: 24,
      finalContext: '<user_memory>Prefers concise answers</user_memory>',
      sections: [
        {
          type: 'semantic_memory',
          label: '长期记忆',
          included: true,
          estimatedTokens: 12,
          content: '<user_memory>Prefers concise answers</user_memory>',
        },
      ],
      flow: [
        { order: 1, title: '判断问题类型', detail: 'STANDARD', status: 'active' },
        { order: 2, title: '加入长期记忆', detail: '命中 1 段', status: 'active' },
      ],
    });
    dialogMocks.askConfirm.mockResolvedValue(true);
  });

  it('loads governed memories and searches with query filters', async () => {
    const user = userEvent.setup();
    render(<MemoryControlPanel />);

    expect(await screen.findByText('用户希望回答简洁、直接。')).toBeInTheDocument();
    expect(screen.getByText('trace-1')).toBeInTheDocument();
    expect(screen.getByText('记住，我希望你回答简洁一点')).toBeInTheDocument();

    await user.type(screen.getByPlaceholderText('搜索内容、证据或来源'), '简洁');
    await user.click(screen.getByRole('button', { name: '搜索记忆' }));

    await waitFor(() => {
      expect(apiMocks.getMemories).toHaveBeenLastCalledWith(expect.objectContaining({
        query: '简洁',
        status: '',
        type: '',
      }));
    });
  });

  it('disables and deletes an individual memory through the governance API', async () => {
    const user = userEvent.setup();
    const activeMemory = makeMemory();
    apiMocks.getMemories.mockResolvedValue({ items: [activeMemory], nextCursor: null });
    apiMocks.updateMemory.mockResolvedValue({ ...activeMemory, status: 'disabled' });
    apiMocks.deleteMemory.mockResolvedValue(undefined);

    render(<MemoryControlPanel />);

    expect(await screen.findByText('用户希望回答简洁、直接。')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '禁用记忆 1' }));

    await waitFor(() => {
      expect(apiMocks.updateMemory).toHaveBeenCalledWith(1, expect.objectContaining({
        status: 'disabled',
      }));
    });
    expect(await screen.findByRole('button', { name: '启用记忆 1' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '删除记忆 1' }));

    await waitFor(() => {
      expect(dialogMocks.askConfirm).toHaveBeenCalled();
      expect(apiMocks.deleteMemory).toHaveBeenCalledWith(1);
    });
    expect(screen.queryByText('用户希望回答简洁、直接。')).not.toBeInTheDocument();
  });
  it('previews the real assembled context and flow for a draft question', async () => {
    const user = userEvent.setup();
    render(<MemoryControlPanel />);

    expect(await screen.findByText('trace-1')).toBeInTheDocument();

    await user.type(screen.getByPlaceholderText('输入问题以预览上下文'), '总结这篇笔记');
    await user.click(screen.getByRole('button', { name: '生成上下文预览' }));

    await waitFor(() => {
      expect(apiMocks.previewContext).toHaveBeenCalledWith({
        query: '总结这篇笔记',
        noteIds: [],
      });
    });
    expect(await screen.findByText('最终上下文')).toBeInTheDocument();
    expect(screen.getByText('长期记忆')).toBeInTheDocument();
    expect(screen.getByText('判断问题类型')).toBeInTheDocument();
    expect(screen.getAllByText('<user_memory>Prefers concise answers</user_memory>')).toHaveLength(2);
  });

  it('shows the memory event ledger with trace ids for frontend verification', async () => {
    render(<MemoryControlPanel />);

    expect(await screen.findByText('事件记录')).toBeInTheDocument();
    expect(screen.getByText('CREATED')).toBeInTheDocument();
    expect(screen.getByText('explicit_memory')).toBeInTheDocument();
    expect(screen.getByText('memory-capture-abc')).toBeInTheDocument();
    expect(apiMocks.getMemoryEvents).toHaveBeenCalledWith({ limit: 50 });
  });
});
