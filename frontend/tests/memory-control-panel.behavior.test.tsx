import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const apiMocks = vi.hoisted(() => ({
  decideMemoryReviewCase: vi.fn(),
  deleteMemory: vi.fn(),
  exportMemories: vi.fn(),
  exportMemoryReplayCandidates: vi.fn(),
  getMemoryEvents: vi.fn(),
  getMemories: vi.fn(),
  getMemoryReviewCases: vi.fn(),
  previewContext: vi.fn(),
  submitMemoryFeedback: vi.fn(),
  updateMemory: vi.fn(),
}));

const dialogMocks = vi.hoisted(() => ({
  askConfirm: vi.fn(),
}));

vi.mock('../src/api', () => ({
  decideMemoryReviewCase: apiMocks.decideMemoryReviewCase,
  deleteMemory: apiMocks.deleteMemory,
  exportMemories: apiMocks.exportMemories,
  exportMemoryReplayCandidates: apiMocks.exportMemoryReplayCandidates,
  getMemoryEvents: apiMocks.getMemoryEvents,
  getMemories: apiMocks.getMemories,
  getMemoryReviewCases: apiMocks.getMemoryReviewCases,
  previewContext: apiMocks.previewContext,
  submitMemoryFeedback: apiMocks.submitMemoryFeedback,
  updateMemory: apiMocks.updateMemory,
}));

vi.mock('../src/services/dialogService', () => ({
  askConfirm: dialogMocks.askConfirm,
}));

import MemoryControlPanel from '../src/components/MemoryControlPanel';
import { useToastStore } from '../src/stores/toastStore';
import type { MemoryRecord, MemoryReviewCaseRecord } from '../src/api';

function makeMemory(overrides: Partial<MemoryRecord> = {}): MemoryRecord {
  return {
    id: 1,
    type: 'semantic',
    memoryType: 'preference',
    category: 'style',
    content: 'User prefers concise direct answers.',
    confidence: 0.92,
    source: 'chat',
    scope: 'global',
    status: 'active',
    sourceTraceId: 'trace-1',
    sourceMessageIds: 'msg-1',
    sourceToolCallId: null,
    evidenceExcerpt: 'Remember that I prefer concise answers.',
    metadataJson: JSON.stringify({
      capture_reason: 'explicit memory request',
      policy_reason: 'user asked the assistant to remember the preference',
      decision_type: 'allow',
      candidate_confidence: 0.91,
      policy_signals: ['explicit_remember', 'advisor_preference_signal'],
      policy_source: 'memory_write_service',
    }),
    lastAccessedAt: null,
    accessCount: 3,
    supersedesId: null,
    createdAt: '2026-07-03T09:00:00',
    updatedAt: '2026-07-03T09:30:00',
    ...overrides,
  };
}

function makeReviewCase(overrides: Partial<MemoryReviewCaseRecord> = {}): MemoryReviewCaseRecord {
  return {
    id: 201,
    memoryId: 1,
    userId: 'user-1',
    feedbackType: 'wrong_memory',
    userComment: 'This memory is inaccurate.',
    expectedContent: 'User wants direct answers.',
    expectedMemoryType: 'preference',
    expectedCaptureAllowed: true,
    status: 'pending_review',
    reviewerId: null,
    reviewerDecision: null,
    reviewerComment: null,
    replayCaseId: 'human-review-201',
    replayCaseJson: '{"caseId":"human-review-201","expectedCaptureAllowed":true}',
    manifestJson: '{"source":"human_review_feedback"}',
    memoryBeforeJson: '{"content":"User prefers concise direct answers."}',
    sourceContextJson: '{"sourceTraceId":"trace-1"}',
    policySnapshotJson: '{"policy_source":"memory_write_service","policy_signals":["explicit_remember"]}',
    createdAt: '2026-07-09T10:00:00',
    updatedAt: '2026-07-09T10:00:00',
    reviewedAt: null,
    ...overrides,
  };
}

function httpError(status: number): Error & { response: { status: number } } {
  return Object.assign(new Error(`HTTP ${status}`), { response: { status } });
}

describe('MemoryControlPanel behavior', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:memory-export'),
    });
    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      value: vi.fn(),
    });
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
          afterJson: '{"content":"User prefers concise direct answers.","metadata":{"policy_signals":["explicit_remember"]}}',
          traceId: 'memory-capture-abc',
          createdAt: '2026-07-03T10:05:00',
        },
      ],
    });
    apiMocks.getMemoryReviewCases.mockResolvedValue({ items: [makeReviewCase()] });
    apiMocks.decideMemoryReviewCase.mockResolvedValue(makeReviewCase({ status: 'approved_for_replay' }));
    apiMocks.exportMemories.mockResolvedValue({ items: [makeMemory()], nextCursor: null });
    apiMocks.exportMemoryReplayCandidates.mockResolvedValue({ items: [makeReviewCase()] });
    apiMocks.previewContext.mockResolvedValue({
      query: 'Summarize this note',
      noteIds: [],
      selectedNoteCount: 0,
      intent: 'STANDARD',
      contextChars: 96,
      estimatedTokens: 24,
      finalContext: '<user_memory>Prefers concise answers</user_memory>',
      sections: [
        {
          type: 'semantic_memory',
          label: 'Long-term memory',
          included: true,
          estimatedTokens: 12,
          content: '<user_memory>Prefers concise answers</user_memory>',
        },
      ],
      flow: [
        { order: 1, title: 'Classify request', detail: 'STANDARD', status: 'active' },
        { order: 2, title: 'Inject long-term memory', detail: '1 section matched', status: 'active' },
      ],
    });
    apiMocks.submitMemoryFeedback.mockResolvedValue(makeReviewCase());
    dialogMocks.askConfirm.mockResolvedValue(true);
  });

  it('loads governed memories and searches with query filters', async () => {
    const user = userEvent.setup();
    render(<MemoryControlPanel />);

    expect(await screen.findByText('User prefers concise direct answers.')).toBeInTheDocument();
    expect(screen.getByText('trace-1')).toBeInTheDocument();
    expect(screen.getByText('Remember that I prefer concise answers.')).toBeInTheDocument();

    await user.type(screen.getByRole('textbox', { name: '搜索记忆' }), 'concise');
    await user.click(screen.getByRole('button', { name: '搜索记忆' }));

    await waitFor(() => {
      expect(apiMocks.getMemories).toHaveBeenLastCalledWith(expect.objectContaining({
        query: 'concise',
        status: 'active',
        type: '',
      }));
    });
  });

  it('renders metadata explanations and loads per-memory events only when expanded', async () => {
    const user = userEvent.setup();
    render(<MemoryControlPanel />);

    expect(await screen.findByText('User prefers concise direct answers.')).toBeInTheDocument();
    expect(apiMocks.getMemoryEvents).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: '展开解释 1' }));

    expect(await screen.findByText('explicit memory request')).toBeInTheDocument();
    expect(screen.getByText('user asked the assistant to remember the preference')).toBeInTheDocument();
    expect(screen.getByText('advisor')).toBeInTheDocument();
    expect(screen.getByText('explicit_remember')).toBeInTheDocument();
    expect(screen.getByText('advisor_preference_signal')).toBeInTheDocument();
    expect(screen.getByText('memory-capture-abc')).toBeInTheDocument();
    expect(apiMocks.getMemoryEvents).toHaveBeenCalledWith({ memoryId: 1, limit: 25 });
    expect(apiMocks.getMemoryEvents).not.toHaveBeenCalledWith({ limit: 50 });
  });

  it('renders a legacy fallback when metadata is missing or invalid', async () => {
    const user = userEvent.setup();
    apiMocks.getMemories.mockResolvedValue({
      items: [makeMemory({ id: 2, metadataJson: '{bad json', sourceTraceId: 'legacy-trace' })],
      nextCursor: null,
    });

    render(<MemoryControlPanel />);

    expect(await screen.findByText('User prefers concise direct answers.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '展开解释 2' }));

    expect(screen.getByText('无结构化解释')).toBeInTheDocument();
    expect(screen.getByText('unknown')).toBeInTheDocument();
    expect(screen.getByText('legacy-trace')).toBeInTheDocument();
  });

  it('submits wrong-memory feedback with exact backend enum values', async () => {
    const user = userEvent.setup();
    render(<MemoryControlPanel />);

    expect(await screen.findByText('User prefers concise direct answers.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '报告错误记忆 1' }));

    await user.selectOptions(screen.getByLabelText('反馈类型'), 'wrong_memory');
    await user.clear(screen.getByLabelText('反馈说明'));
    await user.type(screen.getByLabelText('反馈说明'), '这条记忆不准确');
    await user.clear(screen.getByLabelText('期望内容'));
    await user.type(screen.getByLabelText('期望内容'), '用户希望回答更直接');
    await user.clear(screen.getByLabelText('期望类型'));
    await user.type(screen.getByLabelText('期望类型'), 'preference');
    await user.click(screen.getByRole('button', { name: '提交反馈' }));

    await waitFor(() => {
      expect(apiMocks.submitMemoryFeedback).toHaveBeenCalledWith(1, expect.objectContaining({
        feedbackType: 'wrong_memory',
        userComment: '这条记忆不准确',
        expectedContent: '用户希望回答更直接',
        expectedMemoryType: 'preference',
        expectedCaptureAllowed: true,
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

    expect(await screen.findByText('User prefers concise direct answers.')).toBeInTheDocument();

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
    expect(screen.queryByText('User prefers concise direct answers.')).not.toBeInTheDocument();
  });

  it('previews the real assembled context and flow for a draft question', async () => {
    const user = userEvent.setup();
    render(<MemoryControlPanel />);

    expect(await screen.findByText('trace-1')).toBeInTheDocument();

    await user.type(screen.getByLabelText('上下文预览问题'), 'Summarize this note');
    await user.click(screen.getByRole('button', { name: '生成上下文预览' }));

    await waitFor(() => {
      expect(apiMocks.previewContext).toHaveBeenCalledWith({
        query: 'Summarize this note',
        noteIds: [],
      });
    });
    expect(await screen.findByText('最终上下文')).toBeInTheDocument();
    expect(screen.getByText('Long-term memory')).toBeInTheDocument();
    expect(screen.getByText('Classify request')).toBeInTheDocument();
    expect(screen.getAllByText('<user_memory>Prefers concise answers</user_memory>')).toHaveLength(2);
  });

  it('loads admin review cases lazily and handles 403 without breaking user memories', async () => {
    const user = userEvent.setup();
    apiMocks.getMemoryReviewCases.mockRejectedValue(httpError(403));

    render(<MemoryControlPanel />);

    expect(await screen.findByText('User prefers concise direct answers.')).toBeInTheDocument();
    expect(apiMocks.getMemoryReviewCases).not.toHaveBeenCalled();

    await user.click(screen.getByRole('tab', { name: '审核队列' }));

    expect(await screen.findByText('需要管理员权限')).toBeInTheDocument();
    expect(useToastStore.getState().toasts).toHaveLength(0);

    await user.click(screen.getByRole('tab', { name: '我的记忆' }));
    expect(screen.getByText('User prefers concise direct answers.')).toBeInTheDocument();
  });

  it('renders review cases and submits approve replay decisions', async () => {
    const user = userEvent.setup();
    render(<MemoryControlPanel />);

    await screen.findByText('User prefers concise direct answers.');
    await user.click(screen.getByRole('tab', { name: '审核队列' }));

    expect(await screen.findByText('pending_review')).toBeInTheDocument();
    expect(screen.getByText('wrong_memory')).toBeInTheDocument();
    expect(screen.getByText('This memory is inaccurate.')).toBeInTheDocument();
    expect(screen.getByText('human-review-201')).toBeInTheDocument();
    expect(screen.getByText(/sourceTraceId/)).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText('审核决策 201'), 'approve_replay');
    await user.click(screen.getByRole('button', { name: '提交审核 201' }));

    await waitFor(() => {
      expect(apiMocks.decideMemoryReviewCase).toHaveBeenCalledWith(201, expect.objectContaining({
        decision: 'approve_replay',
      }));
    });
  });

  it('submits update-memory review decisions with corrected fields', async () => {
    const user = userEvent.setup();
    render(<MemoryControlPanel />);

    await screen.findByText('User prefers concise direct answers.');
    await user.click(screen.getByRole('tab', { name: '审核队列' }));
    await screen.findByText('pending_review');

    await user.selectOptions(screen.getByLabelText('审核决策 201'), 'update_memory');
    await user.clear(screen.getByLabelText('修正内容 201'));
    await user.type(screen.getByLabelText('修正内容 201'), 'User prefers direct Chinese answers.');
    await user.clear(screen.getByLabelText('修正类型 201'));
    await user.type(screen.getByLabelText('修正类型 201'), 'preference');
    await user.clear(screen.getByLabelText('修正置信度 201'));
    await user.type(screen.getByLabelText('修正置信度 201'), '0.86');
    await user.type(screen.getByLabelText('审核备注 201'), 'corrected from user feedback');
    await user.click(screen.getByRole('button', { name: '提交审核 201' }));

    await waitFor(() => {
      expect(apiMocks.decideMemoryReviewCase).toHaveBeenCalledWith(201, expect.objectContaining({
        decision: 'update_memory',
        correctedContent: 'User prefers direct Chinese answers.',
        correctedMemoryType: 'preference',
        correctedConfidence: 0.86,
        reviewerComment: 'corrected from user feedback',
      }));
    });
  });

  it('exports approved replay candidates from the review workspace', async () => {
    const user = userEvent.setup();
    render(<MemoryControlPanel />);

    await screen.findByText('User prefers concise direct answers.');
    await user.click(screen.getByRole('tab', { name: '审核队列' }));
    await screen.findByText('pending_review');

    const reviewWorkspace = screen.getByRole('tabpanel', { name: '审核队列' });
    await user.click(within(reviewWorkspace).getByRole('button', { name: '导出 replay 样本' }));

    await waitFor(() => {
      expect(apiMocks.exportMemoryReplayCandidates).toHaveBeenCalledWith(undefined);
      expect(URL.createObjectURL).toHaveBeenCalled();
    });
  });
});
