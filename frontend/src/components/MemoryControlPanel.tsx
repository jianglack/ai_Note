import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ArrowDownTrayIcon,
  ArrowPathIcon,
  CheckCircleIcon,
  ChevronDownIcon,
  EyeSlashIcon,
  MagnifyingGlassIcon,
  QueueListIcon,
  TrashIcon,
  XMarkIcon,
} from '@heroicons/react/24/outline';
import {
  decideMemoryReviewCase,
  deleteMemory,
  exportMemories,
  exportMemoryReplayCandidates,
  getMemoryEvents,
  getMemories,
  getMemoryReviewCases,
  previewContext,
  submitMemoryFeedback,
  updateMemory,
  type ContextPreviewResponse,
  type MemoryEventRecord,
  type MemoryListParams,
  type MemoryRecord,
  type MemoryReviewCaseRecord,
  type MemoryStatus,
} from '../api';
import { askConfirm } from '../services/dialogService';
import { useToastStore } from '../stores/toastStore';

const TYPE_OPTIONS = [
  { value: '', label: '全部类型' },
  { value: 'preference', label: '偏好' },
  { value: 'style', label: '风格' },
  { value: 'fact', label: '事实' },
  { value: 'procedure', label: '流程' },
  { value: 'project_context', label: '项目资料' },
];

const STATUS_OPTIONS = [
  { value: 'active', label: 'active' },
  { value: 'disabled', label: 'disabled' },
  { value: 'deleted', label: 'deleted' },
  { value: 'retracted', label: 'retracted' },
  { value: 'superseded', label: 'superseded' },
  { value: '', label: '全部状态' },
];

const REVIEW_STATUS_OPTIONS = [
  { value: 'pending_review', label: '待审核' },
  { value: 'rejected', label: '已驳回' },
  { value: 'actioned', label: '已处理' },
  { value: 'approved_for_replay', label: '已进 replay' },
  { value: 'duplicate', label: '重复反馈' },
  { value: '', label: '全部状态' },
];

const FEEDBACK_TYPES = [
  { value: 'wrong_memory', label: '记忆内容错误' },
  { value: 'should_not_remember', label: '不应该记录' },
  { value: 'type_wrong', label: '记忆类型错误' },
  { value: 'outdated', label: '已经过时' },
  { value: 'missing_context', label: '缺少上下文' },
  { value: 'other', label: '其他' },
];

const REVIEW_DECISIONS = [
  { value: 'reject_feedback', label: 'reject_feedback' },
  { value: 'disable_memory', label: 'disable_memory' },
  { value: 'delete_memory', label: 'delete_memory' },
  { value: 'update_memory', label: 'update_memory' },
  { value: 'approve_replay', label: 'approve_replay' },
  { value: 'mark_duplicate', label: 'mark_duplicate' },
];

type Workspace = 'memories' | 'review';

type ParsedJson = {
  value: Record<string, unknown> | null;
  raw: string | null;
  invalid: boolean;
};

type Explanation = {
  captureReason: string | null;
  policyReason: string | null;
  decisionType: string | null;
  candidateConfidence: number | null;
  policySignals: string[];
  policySource: string | null;
  advisorInvolvement: 'advisor' | 'rules' | 'unknown';
  hasStructuredExplanation: boolean;
  raw: string | null;
  invalid: boolean;
};

type FeedbackForm = {
  feedbackType: string;
  userComment: string;
  expectedContent: string;
  expectedMemoryType: string;
  expectedCaptureAllowed: boolean;
};

type DecisionForm = {
  decision: string;
  reviewerComment: string;
  correctedContent: string;
  correctedMemoryType: string;
  correctedConfidence: string;
};

function formatDate(value: string | null) {
  if (!value) return '未知';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

function formatConfidence(value: number | null) {
  if (value === null || value === undefined) return 'n/a';
  return `${Math.round(value * 100)}%`;
}

function downloadJson(payload: unknown, filename: string) {
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function parseJsonObject(value: string | null): ParsedJson {
  if (!value) {
    return { value: null, raw: null, invalid: false };
  }
  try {
    const parsed = JSON.parse(value);
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return { value: parsed as Record<string, unknown>, raw: value, invalid: false };
    }
    return { value: null, raw: value, invalid: true };
  } catch {
    return { value: null, raw: value, invalid: true };
  }
}

function asString(value: unknown): string | null {
  return typeof value === 'string' && value.trim().length > 0 ? value : null;
}

function asNumber(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function asStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.filter((item): item is string => typeof item === 'string' && item.trim().length > 0);
}

function getExplanation(memory: MemoryRecord): Explanation {
  const parsed = parseJsonObject(memory.metadataJson);
  const metadata = parsed.value;
  const captureReason = asString(metadata?.capture_reason);
  const policyReason = asString(metadata?.policy_reason);
  const decisionType = asString(metadata?.decision_type);
  const candidateConfidence = asNumber(metadata?.candidate_confidence);
  const policySignals = asStringArray(metadata?.policy_signals);
  const policySource = asString(metadata?.policy_source);
  const hasStructuredExplanation = Boolean(
    captureReason
    || policyReason
    || decisionType
    || candidateConfidence !== null
    || policySignals.length > 0
    || policySource,
  );
  const sourceHasAdvisor = Boolean(policySource?.toLowerCase().includes('advisor'));
  const signalsHaveAdvisor = policySignals.some(signal => signal.startsWith('advisor_'));
  const advisorInvolvement = !hasStructuredExplanation
    ? 'unknown'
    : sourceHasAdvisor || signalsHaveAdvisor
      ? 'advisor'
      : policySignals.length > 0 || policySource
        ? 'rules'
        : 'unknown';

  return {
    captureReason,
    policyReason,
    decisionType,
    candidateConfidence,
    policySignals,
    policySource,
    advisorInvolvement,
    hasStructuredExplanation,
    raw: parsed.raw,
    invalid: parsed.invalid,
  };
}

function isForbiddenError(err: unknown) {
  return Boolean(
    err
    && typeof err === 'object'
    && 'response' in err
    && (err as { response?: { status?: number } }).response?.status === 403,
  );
}

function jsonPreview(value: string | null) {
  const parsed = parseJsonObject(value);
  if (!value) return 'n/a';
  if (parsed.value) return JSON.stringify(parsed.value, null, 2);
  return value;
}

function MemoryEventList({
  events,
  loading,
  error,
}: {
  events: MemoryEventRecord[];
  loading: boolean;
  error: string | null;
}) {
  if (loading) {
    return <p className="memory-muted">事件加载中</p>;
  }
  if (error) {
    return <p className="memory-error">{error}</p>;
  }
  if (events.length === 0) {
    return <p className="memory-muted">暂无该记忆的审计事件。</p>;
  }

  return (
    <ol className="memory-event-list compact">
      {events.map(event => (
        <li className="memory-event-item" key={event.id}>
          <div className="memory-event-icon" aria-hidden="true">
            <QueueListIcon />
          </div>
          <div className="memory-event-main">
            <div className="memory-event-topline">
              <span className={`memory-event-type memory-event-type-${event.eventType.toLowerCase()}`}>
                {event.eventType}
              </span>
              <span className="memory-muted">{formatDate(event.createdAt)}</span>
              <span className="memory-muted">{event.traceId || 'n/a'}</span>
            </div>
            <dl className="memory-event-meta">
              <div>
                <dt>原因</dt>
                <dd>{event.reason || 'n/a'}</dd>
              </div>
              <div>
                <dt>Actor</dt>
                <dd>{event.actor || 'system'}</dd>
              </div>
              <div>
                <dt>Memory</dt>
                <dd>{event.memoryId ?? 'n/a'}</dd>
              </div>
            </dl>
            {(event.beforeJson || event.afterJson) && (
              <details className="memory-event-snapshot">
                <summary>变更快照</summary>
                {event.beforeJson && <pre>{jsonPreview(event.beforeJson)}</pre>}
                {event.afterJson && <pre>{jsonPreview(event.afterJson)}</pre>}
              </details>
            )}
          </div>
        </li>
      ))}
    </ol>
  );
}

export default function MemoryControlPanel() {
  const addToast = useToastStore(state => state.addToast);
  const [workspace, setWorkspace] = useState<Workspace>('memories');
  const [memories, setMemories] = useState<MemoryRecord[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [request, setRequest] = useState<MemoryListParams>({ type: '', status: 'active', query: '' });
  const [typeFilter, setTypeFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('active');
  const [queryInput, setQueryInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busyMemoryId, setBusyMemoryId] = useState<number | null>(null);
  const [exporting, setExporting] = useState(false);
  const [expandedMemoryIds, setExpandedMemoryIds] = useState<Set<number>>(() => new Set());
  const [memoryEvents, setMemoryEvents] = useState<Record<number, MemoryEventRecord[]>>({});
  const [eventsLoading, setEventsLoading] = useState<Set<number>>(() => new Set());
  const [eventsError, setEventsError] = useState<Record<number, string | null>>({});
  const [feedbackMemory, setFeedbackMemory] = useState<MemoryRecord | null>(null);
  const [feedbackSubmitting, setFeedbackSubmitting] = useState(false);
  const [feedbackForm, setFeedbackForm] = useState<FeedbackForm>({
    feedbackType: 'wrong_memory',
    userComment: '',
    expectedContent: '',
    expectedMemoryType: '',
    expectedCaptureAllowed: true,
  });
  const [previewQuery, setPreviewQuery] = useState('');
  const [previewNoteIds, setPreviewNoteIds] = useState('');
  const [preview, setPreview] = useState<ContextPreviewResponse | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [reviewStatus, setReviewStatus] = useState('pending_review');
  const [reviewCases, setReviewCases] = useState<MemoryReviewCaseRecord[]>([]);
  const [reviewLoaded, setReviewLoaded] = useState(false);
  const [reviewLoading, setReviewLoading] = useState(false);
  const [reviewForbidden, setReviewForbidden] = useState(false);
  const [reviewError, setReviewError] = useState<string | null>(null);
  const [decisionForms, setDecisionForms] = useState<Record<number, DecisionForm>>({});
  const [busyReviewCaseId, setBusyReviewCaseId] = useState<number | null>(null);
  const [replayExporting, setReplayExporting] = useState(false);

  const activeCount = useMemo(
    () => memories.filter(memory => memory.status === 'active' || !memory.status).length,
    [memories],
  );

  const loadMemories = useCallback(async (options: { cursor?: string | null; append?: boolean } = {}) => {
    setLoading(true);
    setError(null);
    try {
      const response = await getMemories({
        ...request,
        cursor: options.cursor ?? undefined,
      });
      setMemories(current => options.append ? [...current, ...response.items] : response.items);
      setNextCursor(response.nextCursor);
    } catch (err) {
      const message = err instanceof Error ? err.message : '记忆列表加载失败';
      setError(message);
      addToast({ type: 'failed', title: '记忆加载失败', message });
    } finally {
      setLoading(false);
    }
  }, [addToast, request]);

  useEffect(() => {
    void loadMemories();
  }, [loadMemories]);

  const loadMemoryEvents = useCallback(async (memoryId: number) => {
    setEventsLoading(current => new Set(current).add(memoryId));
    setEventsError(current => ({ ...current, [memoryId]: null }));
    try {
      const response = await getMemoryEvents({ memoryId, limit: 25 });
      setMemoryEvents(current => ({ ...current, [memoryId]: response.items }));
    } catch (err) {
      const message = err instanceof Error ? err.message : '记忆事件加载失败';
      setEventsError(current => ({ ...current, [memoryId]: message }));
      addToast({ type: 'failed', title: '记忆事件加载失败', message });
    } finally {
      setEventsLoading(current => {
        const next = new Set(current);
        next.delete(memoryId);
        return next;
      });
    }
  }, [addToast]);

  const loadReviewCases = useCallback(async () => {
    setReviewLoading(true);
    setReviewError(null);
    setReviewForbidden(false);
    try {
      const response = await getMemoryReviewCases({
        status: reviewStatus || undefined,
        limit: 50,
      });
      setReviewCases(response.items);
      setReviewLoaded(true);
      setDecisionForms(Object.fromEntries(response.items.map(item => [
        item.id,
        {
          decision: 'reject_feedback',
          reviewerComment: '',
          correctedContent: item.expectedContent || '',
          correctedMemoryType: item.expectedMemoryType || '',
          correctedConfidence: '',
        },
      ])));
    } catch (err) {
      if (isForbiddenError(err)) {
        setReviewForbidden(true);
        setReviewLoaded(true);
      } else {
        const message = err instanceof Error ? err.message : '审核队列加载失败';
        setReviewError(message);
        addToast({ type: 'failed', title: '审核队列加载失败', message });
      }
    } finally {
      setReviewLoading(false);
    }
  }, [addToast, reviewStatus]);

  useEffect(() => {
    if (workspace === 'review') {
      void loadReviewCases();
    }
  }, [loadReviewCases, workspace]);

  const applyFilters = (next: MemoryListParams = {
    type: typeFilter,
    status: statusFilter,
    query: queryInput.trim(),
  }) => {
    setRequest(next);
  };

  const handleStatusChange = (value: string) => {
    setStatusFilter(value);
    applyFilters({ type: typeFilter, status: value, query: queryInput.trim() });
  };

  const handleTypeChange = (value: string) => {
    setTypeFilter(value);
    applyFilters({ type: value, status: statusFilter, query: queryInput.trim() });
  };

  const toggleMemoryDetails = (memoryId: number) => {
    setExpandedMemoryIds(current => {
      const next = new Set(current);
      if (next.has(memoryId)) {
        next.delete(memoryId);
        return next;
      }
      next.add(memoryId);
      if (!memoryEvents[memoryId] && !eventsLoading.has(memoryId)) {
        void loadMemoryEvents(memoryId);
      }
      return next;
    });
  };

  const toggleMemoryStatus = async (memory: MemoryRecord) => {
    const nextStatus: MemoryStatus = memory.status === 'disabled' ? 'active' : 'disabled';
    setBusyMemoryId(memory.id);
    try {
      const updated = await updateMemory(memory.id, {
        status: nextStatus,
        reason: `user ${nextStatus === 'active' ? 'enabled' : 'disabled'} memory from control panel`,
      });
      setMemories(current => current.map(item => item.id === updated.id ? updated : item));
      addToast({
        type: 'success',
        title: nextStatus === 'active' ? '记忆已启用' : '记忆已禁用',
      });
    } catch (err) {
      const message = err instanceof Error ? err.message : '记忆状态更新失败';
      addToast({ type: 'failed', title: '记忆状态更新失败', message });
    } finally {
      setBusyMemoryId(null);
    }
  };

  const deleteMemoryItem = async (memory: MemoryRecord) => {
    const confirmed = await askConfirm({
      title: '删除这条记忆？',
      message: '删除后它不会再进入模型上下文，审计事件会保留。',
      confirmLabel: '删除',
      cancelLabel: '取消',
      danger: true,
    });
    if (!confirmed) return;

    setBusyMemoryId(memory.id);
    try {
      await deleteMemory(memory.id);
      setMemories(current => current.filter(item => item.id !== memory.id));
      addToast({ type: 'success', title: '记忆已删除' });
    } catch (err) {
      const message = err instanceof Error ? err.message : '记忆删除失败';
      addToast({ type: 'failed', title: '记忆删除失败', message });
    } finally {
      setBusyMemoryId(null);
    }
  };

  const openFeedback = (memory: MemoryRecord) => {
    setFeedbackMemory(memory);
    setFeedbackForm({
      feedbackType: 'wrong_memory',
      userComment: '',
      expectedContent: memory.content,
      expectedMemoryType: memory.memoryType || memory.category || '',
      expectedCaptureAllowed: true,
    });
  };

  const handleSubmitFeedback = async () => {
    if (!feedbackMemory) return;
    setFeedbackSubmitting(true);
    try {
      await submitMemoryFeedback(feedbackMemory.id, {
        feedbackType: feedbackForm.feedbackType,
        userComment: feedbackForm.userComment.trim() || null,
        expectedContent: feedbackForm.expectedContent.trim() || null,
        expectedMemoryType: feedbackForm.expectedMemoryType.trim() || null,
        expectedCaptureAllowed: feedbackForm.expectedCaptureAllowed,
      });
      setFeedbackMemory(null);
      addToast({ type: 'success', title: '记忆反馈已提交' });
    } catch (err) {
      const message = err instanceof Error ? err.message : '记忆反馈提交失败';
      addToast({ type: 'failed', title: '记忆反馈提交失败', message });
    } finally {
      setFeedbackSubmitting(false);
    }
  };

  const handleExport = async () => {
    setExporting(true);
    try {
      const payload = await exportMemories();
      downloadJson(payload, `ainote-memories-${new Date().toISOString().slice(0, 10)}.json`);
      addToast({ type: 'success', title: '记忆导出已生成' });
    } catch (err) {
      const message = err instanceof Error ? err.message : '记忆导出失败';
      addToast({ type: 'failed', title: '记忆导出失败', message });
    } finally {
      setExporting(false);
    }
  };

  const handlePreview = async () => {
    const query = previewQuery.trim();
    if (!query) {
      setPreviewError('请输入问题后再生成上下文预览');
      return;
    }

    const noteIds = previewNoteIds
      .split(',')
      .map(id => id.trim())
      .filter(Boolean);

    setPreviewLoading(true);
    setPreviewError(null);
    try {
      const response = await previewContext({ query, noteIds });
      setPreview(response);
    } catch (err) {
      const message = err instanceof Error ? err.message : '上下文预览加载失败';
      setPreviewError(message);
      addToast({ type: 'failed', title: '上下文预览加载失败', message });
    } finally {
      setPreviewLoading(false);
    }
  };

  const updateDecisionForm = (id: number, patch: Partial<DecisionForm>) => {
    setDecisionForms(current => {
      const previous = current[id] || {
        decision: 'reject_feedback',
        reviewerComment: '',
        correctedContent: '',
        correctedMemoryType: '',
        correctedConfidence: '',
      };
      return {
        ...current,
        [id]: {
          ...previous,
          ...patch,
        },
      };
    });
  };

  const submitDecision = async (reviewCase: MemoryReviewCaseRecord) => {
    const form = decisionForms[reviewCase.id] || {
      decision: 'reject_feedback',
      reviewerComment: '',
      correctedContent: '',
      correctedMemoryType: '',
      correctedConfidence: '',
    };
    const correctedConfidence = form.correctedConfidence.trim()
      ? Number(form.correctedConfidence)
      : null;
    setBusyReviewCaseId(reviewCase.id);
    try {
      await decideMemoryReviewCase(reviewCase.id, {
        decision: form.decision,
        reviewerComment: form.reviewerComment.trim() || null,
        correctedContent: form.correctedContent.trim() || null,
        correctedMemoryType: form.correctedMemoryType.trim() || null,
        correctedConfidence: Number.isFinite(correctedConfidence) ? correctedConfidence : null,
      });
      addToast({ type: 'success', title: '审核决策已提交' });
      void loadReviewCases();
    } catch (err) {
      const message = err instanceof Error ? err.message : '审核决策提交失败';
      addToast({ type: 'failed', title: '审核决策提交失败', message });
    } finally {
      setBusyReviewCaseId(null);
    }
  };

  const handleReplayExport = async () => {
    setReplayExporting(true);
    try {
      const payload = await exportMemoryReplayCandidates(undefined);
      downloadJson(payload, `ainote-memory-replay-candidates-${new Date().toISOString().slice(0, 10)}.json`);
      addToast({ type: 'success', title: 'Replay 样本已导出' });
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Replay 样本导出失败';
      addToast({ type: 'failed', title: 'Replay 样本导出失败', message });
    } finally {
      setReplayExporting(false);
    }
  };

  return (
    <section className="memory-control-panel">
      <div className="memory-panel-header">
        <div>
          <h1 className="settings-page-title">AI 记忆</h1>
          <p className="settings-page-desc">
            查看、解释、纠错、禁用、删除和导出长期记忆。禁用或删除后的记忆不会进入模型上下文。
          </p>
        </div>
        <button
          type="button"
          className="memory-icon-button"
          aria-label="导出记忆"
          title="导出记忆"
          onClick={handleExport}
          disabled={exporting}
        >
          <ArrowDownTrayIcon aria-hidden="true" />
        </button>
      </div>

      <div className="memory-workspace-tabs" role="tablist" aria-label="记忆工作区">
        <button
          type="button"
          role="tab"
          aria-selected={workspace === 'memories'}
          className={workspace === 'memories' ? 'active' : ''}
          onClick={() => setWorkspace('memories')}
        >
          我的记忆
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={workspace === 'review'}
          className={workspace === 'review' ? 'active' : ''}
          onClick={() => setWorkspace('review')}
        >
          审核队列
        </button>
      </div>

      {workspace === 'memories' ? (
        <div role="tabpanel" aria-label="我的记忆">
          <section className="context-preview-panel" aria-label="上下文预览">
            <div className="context-preview-header">
              <div>
                <h2>上下文预览</h2>
                <p>输入一个问题，查看本次模型会收到的真实上下文和组装流程。</p>
              </div>
              {preview && (
                <div className="context-preview-stats">
                  <span>{preview.intent}</span>
                  <span>{preview.estimatedTokens} tokens</span>
                  <span>{preview.contextChars} 字符</span>
                </div>
              )}
            </div>

            <div className="context-preview-form">
              <label className="memory-search context-preview-query">
                <MagnifyingGlassIcon aria-hidden="true" />
                <input
                  aria-label="上下文预览问题"
                  value={previewQuery}
                  onChange={event => setPreviewQuery(event.target.value)}
                  placeholder="输入问题以预览上下文"
                />
              </label>
              <input
                className="context-preview-note-input"
                aria-label="选中笔记 ID"
                value={previewNoteIds}
                onChange={event => setPreviewNoteIds(event.target.value)}
                placeholder="可选：笔记 ID，用逗号分隔"
              />
              <button
                type="button"
                className="memory-secondary-button"
                onClick={() => void handlePreview()}
                disabled={previewLoading}
              >
                {previewLoading ? '生成中' : '生成上下文预览'}
              </button>
            </div>

            {previewError && <div className="memory-error">{previewError}</div>}

            {preview && (
              <div className="context-preview-result">
                <div className="context-preview-flow">
                  <h3>组装流程</h3>
                  <ol>
                    {preview.flow.map(step => (
                      <li key={step.order} data-status={step.status}>
                        <span>{step.title}</span>
                        <small>{step.detail}</small>
                      </li>
                    ))}
                  </ol>
                </div>

                <div className="context-preview-sections">
                  <h3>项目上下文分段</h3>
                  {preview.sections.length === 0 ? (
                    <p className="memory-muted">本次没有注入长期记忆、选中笔记或 RAG 片段。</p>
                  ) : (
                    preview.sections.map(section => (
                      <details key={section.type} open>
                        <summary>
                          <span>{section.label}</span>
                          <small>{section.estimatedTokens} tokens</small>
                        </summary>
                        <pre>{section.content}</pre>
                      </details>
                    ))
                  )}
                </div>

                <details className="context-preview-final" open>
                  <summary>最终上下文</summary>
                  <pre>{preview.finalContext || '本次最终上下文为空。'}</pre>
                </details>
              </div>
            )}
          </section>

          <div className="memory-toolbar">
            <label className="memory-search">
              <MagnifyingGlassIcon aria-hidden="true" />
              <input
                aria-label="搜索记忆"
                value={queryInput}
                onChange={event => setQueryInput(event.target.value)}
                onKeyDown={event => {
                  if (event.key === 'Enter') applyFilters();
                }}
                placeholder="搜索内容、证据或来源"
              />
            </label>
            <select
              className="memory-select"
              aria-label="记忆类型"
              value={typeFilter}
              onChange={event => handleTypeChange(event.target.value)}
            >
              {TYPE_OPTIONS.map(option => (
                <option key={option.value || 'all'} value={option.value}>{option.label}</option>
              ))}
            </select>
            <select
              className="memory-select"
              aria-label="记忆状态"
              value={statusFilter}
              onChange={event => handleStatusChange(event.target.value)}
            >
              {STATUS_OPTIONS.map(option => (
                <option key={option.value || 'all'} value={option.value}>{option.label}</option>
              ))}
            </select>
            <button
              type="button"
              className="memory-secondary-button"
              onClick={() => applyFilters()}
              aria-label="搜索记忆"
            >
              搜索
            </button>
            <button
              type="button"
              className="memory-icon-button"
              onClick={() => void loadMemories()}
              aria-label="刷新记忆"
              title="刷新记忆"
              disabled={loading}
            >
              <ArrowPathIcon aria-hidden="true" />
            </button>
          </div>

          <div className="memory-summary" aria-live="polite">
            <span>{memories.length} 条可见记忆</span>
            <span>{activeCount} 条 active</span>
            {loading && <span>加载中</span>}
            {error && <span className="memory-error">{error}</span>}
          </div>

          {memories.length === 0 && !loading ? (
            <div className="memory-empty">暂无符合条件的长期记忆。</div>
          ) : (
            <ul className="memory-list">
              {memories.map(memory => {
                const canToggle = memory.status === 'active' || memory.status === 'disabled' || !memory.status;
                const toggledOff = memory.status === 'disabled';
                const expanded = expandedMemoryIds.has(memory.id);
                const explanation = getExplanation(memory);
                return (
                  <li className="memory-list-item" key={memory.id}>
                    <div className="memory-item-main">
                      <div className="memory-item-topline">
                        <span className="memory-chip">{memory.memoryType || memory.category || memory.type}</span>
                        <span className={`memory-status memory-status-${memory.status || 'active'}`}>
                          {memory.status || 'active'}
                        </span>
                        <span className="memory-muted">confidence {formatConfidence(memory.confidence)}</span>
                      </div>
                      <p className="memory-content">{memory.content}</p>
                      {memory.evidenceExcerpt && (
                        <blockquote className="memory-evidence">{memory.evidenceExcerpt}</blockquote>
                      )}
                      <dl className="memory-meta">
                        <div>
                          <dt>来源</dt>
                          <dd>{memory.source || 'unknown'}</dd>
                        </div>
                        <div>
                          <dt>Trace</dt>
                          <dd>{memory.sourceTraceId || 'n/a'}</dd>
                        </div>
                        <div>
                          <dt>范围</dt>
                          <dd>{memory.scope || 'global'}</dd>
                        </div>
                        <div>
                          <dt>更新</dt>
                          <dd>{formatDate(memory.updatedAt || memory.createdAt)}</dd>
                        </div>
                        <div>
                          <dt>使用</dt>
                          <dd>{memory.accessCount ?? 0}</dd>
                        </div>
                      </dl>

                      {expanded && (
                        <div className="memory-explanation-panel">
                          <div className="memory-explanation-header">
                            <h3>记录解释</h3>
                            <span className="memory-status">{explanation.advisorInvolvement}</span>
                          </div>
                          {!explanation.hasStructuredExplanation && (
                            <p className="memory-empty compact">无结构化解释</p>
                          )}
                          <dl className="memory-explanation-grid">
                            <div>
                              <dt>为什么记录</dt>
                              <dd>{explanation.captureReason || 'n/a'}</dd>
                            </div>
                            <div>
                              <dt>策略原因</dt>
                              <dd>{explanation.policyReason || 'n/a'}</dd>
                            </div>
                            <div>
                              <dt>决策</dt>
                              <dd>{explanation.decisionType || 'n/a'}</dd>
                            </div>
                            <div>
                              <dt>候选置信度</dt>
                              <dd>{formatConfidence(explanation.candidateConfidence)}</dd>
                            </div>
                            <div>
                              <dt>策略来源</dt>
                              <dd>{explanation.policySource || 'n/a'}</dd>
                            </div>
                            <div>
                              <dt>Supersedes</dt>
                              <dd>{memory.supersedesId ?? 'n/a'}</dd>
                            </div>
                            <div>
                              <dt>消息引用</dt>
                              <dd>{memory.sourceMessageIds || 'n/a'}</dd>
                            </div>
                            <div>
                              <dt>Tool call</dt>
                              <dd>{memory.sourceToolCallId || 'n/a'}</dd>
                            </div>
                          </dl>
                          <div className="memory-event-policy" aria-label="策略信号">
                            <span>策略信号</span>
                            {explanation.policySignals.length === 0 ? (
                              <span className="memory-muted">n/a</span>
                            ) : (
                              explanation.policySignals.map(signal => (
                                <span className="memory-signal-chip" key={`${memory.id}-${signal}`}>
                                  {signal}
                                </span>
                              ))
                            )}
                          </div>
                          <details className="memory-event-snapshot">
                            <summary>原始 metadata</summary>
                            <pre>{explanation.raw || 'n/a'}</pre>
                          </details>
                          <div className="memory-explanation-events">
                            <h3>该记忆事件</h3>
                            <MemoryEventList
                              events={memoryEvents[memory.id] || []}
                              loading={eventsLoading.has(memory.id)}
                              error={eventsError[memory.id] || null}
                            />
                          </div>
                        </div>
                      )}
                    </div>
                    <div className="memory-actions">
                      <button
                        type="button"
                        className="memory-icon-button"
                        aria-label={`${expanded ? '收起解释' : '展开解释'} ${memory.id}`}
                        title={expanded ? '收起解释' : '展开解释'}
                        onClick={() => toggleMemoryDetails(memory.id)}
                      >
                        <ChevronDownIcon aria-hidden="true" />
                      </button>
                      <button
                        type="button"
                        className="memory-secondary-button"
                        aria-label={`报告错误记忆 ${memory.id}`}
                        onClick={() => openFeedback(memory)}
                      >
                        反馈
                      </button>
                      <button
                        type="button"
                        className="memory-icon-button"
                        aria-label={`${toggledOff ? '启用' : '禁用'}记忆 ${memory.id}`}
                        title={toggledOff ? '启用记忆' : '禁用记忆'}
                        disabled={!canToggle || busyMemoryId === memory.id}
                        onClick={() => void toggleMemoryStatus(memory)}
                      >
                        {toggledOff ? <CheckCircleIcon aria-hidden="true" /> : <EyeSlashIcon aria-hidden="true" />}
                      </button>
                      <button
                        type="button"
                        className="memory-icon-button danger"
                        aria-label={`删除记忆 ${memory.id}`}
                        title="删除记忆"
                        disabled={busyMemoryId === memory.id || memory.status === 'deleted'}
                        onClick={() => void deleteMemoryItem(memory)}
                      >
                        <TrashIcon aria-hidden="true" />
                      </button>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}

          {nextCursor && (
            <button
              type="button"
              className="memory-secondary-button memory-load-more"
              onClick={() => void loadMemories({ cursor: nextCursor, append: true })}
              disabled={loading}
            >
              加载更多
            </button>
          )}
        </div>
      ) : (
        <div role="tabpanel" aria-label="审核队列" className="memory-review-panel">
          <div className="memory-review-toolbar">
            <select
              className="memory-select"
              aria-label="审核状态"
              value={reviewStatus}
              onChange={event => setReviewStatus(event.target.value)}
            >
              {REVIEW_STATUS_OPTIONS.map(option => (
                <option key={option.value || 'all'} value={option.value}>{option.label}</option>
              ))}
            </select>
            <button
              type="button"
              className="memory-icon-button"
              onClick={() => void loadReviewCases()}
              aria-label="刷新审核队列"
              title="刷新审核队列"
              disabled={reviewLoading}
            >
              <ArrowPathIcon aria-hidden="true" />
            </button>
            <button
              type="button"
              className="memory-secondary-button"
              onClick={() => void handleReplayExport()}
              disabled={replayExporting}
            >
              导出 replay 样本
            </button>
          </div>

          {reviewForbidden && (
            <div className="memory-empty">需要管理员权限</div>
          )}
          {reviewError && <div className="memory-error">{reviewError}</div>}
          {reviewLoading && <div className="memory-summary"><span>审核队列加载中</span></div>}
          {reviewLoaded && !reviewForbidden && reviewCases.length === 0 && !reviewLoading && (
            <div className="memory-empty">暂无审核样本。</div>
          )}

          {!reviewForbidden && reviewCases.length > 0 && (
            <ul className="memory-review-list">
              {reviewCases.map(reviewCase => {
                const form = decisionForms[reviewCase.id] || {
                  decision: 'reject_feedback',
                  reviewerComment: '',
                  correctedContent: reviewCase.expectedContent || '',
                  correctedMemoryType: reviewCase.expectedMemoryType || '',
                  correctedConfidence: '',
                };
                return (
                  <li className="memory-review-item" key={reviewCase.id}>
                    <div className="memory-review-topline">
                      <span className="memory-chip">Review #{reviewCase.id}</span>
                      <span className="memory-status">{reviewCase.status}</span>
                      <span className="memory-status">{reviewCase.feedbackType}</span>
                      {reviewCase.replayCaseId && (
                        <span className="memory-muted">{reviewCase.replayCaseId}</span>
                      )}
                    </div>
                    <dl className="memory-explanation-grid">
                      <div>
                        <dt>Memory</dt>
                        <dd>{reviewCase.memoryId ?? 'n/a'}</dd>
                      </div>
                      <div>
                        <dt>用户反馈</dt>
                        <dd>{reviewCase.userComment || 'n/a'}</dd>
                      </div>
                      <div>
                        <dt>期望内容</dt>
                        <dd>{reviewCase.expectedContent || 'n/a'}</dd>
                      </div>
                      <div>
                        <dt>期望类型</dt>
                        <dd>{reviewCase.expectedMemoryType || 'n/a'}</dd>
                      </div>
                      <div>
                        <dt>期望捕获</dt>
                        <dd>{String(reviewCase.expectedCaptureAllowed)}</dd>
                      </div>
                      <div>
                        <dt>创建时间</dt>
                        <dd>{formatDate(reviewCase.createdAt)}</dd>
                      </div>
                    </dl>
                    <div className="memory-review-snapshots">
                      <details open>
                        <summary>记忆快照</summary>
                        <pre>{jsonPreview(reviewCase.memoryBeforeJson)}</pre>
                      </details>
                      <details open>
                        <summary>来源上下文</summary>
                        <pre>{jsonPreview(reviewCase.sourceContextJson)}</pre>
                      </details>
                      <details>
                        <summary>策略快照</summary>
                        <pre>{jsonPreview(reviewCase.policySnapshotJson)}</pre>
                      </details>
                      <details>
                        <summary>Replay 样本</summary>
                        <pre>{jsonPreview(reviewCase.replayCaseJson)}</pre>
                      </details>
                    </div>
                    <div className="memory-review-form">
                      <label>
                        审核决策
                        <select
                          aria-label={`审核决策 ${reviewCase.id}`}
                          className="memory-select"
                          value={form.decision}
                          onChange={event => updateDecisionForm(reviewCase.id, { decision: event.target.value })}
                        >
                          {REVIEW_DECISIONS.map(decision => (
                            <option key={decision.value} value={decision.value}>{decision.label}</option>
                          ))}
                        </select>
                      </label>
                      <label>
                        修正内容
                        <input
                          aria-label={`修正内容 ${reviewCase.id}`}
                          value={form.correctedContent}
                          onChange={event => updateDecisionForm(reviewCase.id, { correctedContent: event.target.value })}
                        />
                      </label>
                      <label>
                        修正类型
                        <input
                          aria-label={`修正类型 ${reviewCase.id}`}
                          value={form.correctedMemoryType}
                          onChange={event => updateDecisionForm(reviewCase.id, { correctedMemoryType: event.target.value })}
                        />
                      </label>
                      <label>
                        修正置信度
                        <input
                          aria-label={`修正置信度 ${reviewCase.id}`}
                          value={form.correctedConfidence}
                          onChange={event => updateDecisionForm(reviewCase.id, { correctedConfidence: event.target.value })}
                          inputMode="decimal"
                        />
                      </label>
                      <label className="memory-review-comment">
                        审核备注
                        <textarea
                          aria-label={`审核备注 ${reviewCase.id}`}
                          value={form.reviewerComment}
                          onChange={event => updateDecisionForm(reviewCase.id, { reviewerComment: event.target.value })}
                        />
                      </label>
                      <button
                        type="button"
                        className="memory-secondary-button"
                        onClick={() => void submitDecision(reviewCase)}
                        disabled={busyReviewCaseId === reviewCase.id}
                      >
                        提交审核 {reviewCase.id}
                      </button>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      )}

      {feedbackMemory && (
        <div className="memory-feedback-backdrop" role="presentation">
          <div className="memory-feedback-dialog" role="dialog" aria-modal="true" aria-label="报告错误记忆">
            <div className="memory-feedback-header">
              <h2>报告错误记忆</h2>
              <button
                type="button"
                className="memory-icon-button"
                aria-label="关闭反馈"
                onClick={() => setFeedbackMemory(null)}
              >
                <XMarkIcon aria-hidden="true" />
              </button>
            </div>
            <p className="memory-muted">{feedbackMemory.content}</p>
            <label>
              反馈类型
              <select
                aria-label="反馈类型"
                className="memory-select"
                value={feedbackForm.feedbackType}
                onChange={event => setFeedbackForm(current => ({ ...current, feedbackType: event.target.value }))}
              >
                {FEEDBACK_TYPES.map(type => (
                  <option key={type.value} value={type.value}>{type.label}</option>
                ))}
              </select>
            </label>
            <label>
              反馈说明
              <textarea
                aria-label="反馈说明"
                value={feedbackForm.userComment}
                onChange={event => setFeedbackForm(current => ({ ...current, userComment: event.target.value }))}
              />
            </label>
            <label>
              期望内容
              <textarea
                aria-label="期望内容"
                value={feedbackForm.expectedContent}
                onChange={event => setFeedbackForm(current => ({ ...current, expectedContent: event.target.value }))}
              />
            </label>
            <label>
              期望类型
              <input
                aria-label="期望类型"
                value={feedbackForm.expectedMemoryType}
                onChange={event => setFeedbackForm(current => ({ ...current, expectedMemoryType: event.target.value }))}
              />
            </label>
            <label className="memory-feedback-checkbox">
              <input
                type="checkbox"
                checked={feedbackForm.expectedCaptureAllowed}
                onChange={event => setFeedbackForm(current => ({
                  ...current,
                  expectedCaptureAllowed: event.target.checked,
                }))}
              />
              应该允许记录这类记忆
            </label>
            <div className="memory-feedback-actions">
              <button
                type="button"
                className="memory-secondary-button"
                onClick={() => setFeedbackMemory(null)}
              >
                取消
              </button>
              <button
                type="button"
                className="memory-secondary-button"
                onClick={() => void handleSubmitFeedback()}
                disabled={feedbackSubmitting}
              >
                提交反馈
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
