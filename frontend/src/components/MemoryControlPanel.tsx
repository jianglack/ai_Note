import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ArrowDownTrayIcon,
  ArrowPathIcon,
  CheckCircleIcon,
  EyeSlashIcon,
  MagnifyingGlassIcon,
  QueueListIcon,
  TrashIcon,
} from '@heroicons/react/24/outline';
import {
  deleteMemory,
  exportMemories,
  getMemoryEvents,
  getMemories,
  previewContext,
  updateMemory,
  type ContextPreviewResponse,
  type MemoryEventRecord,
  type MemoryListParams,
  type MemoryRecord,
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
  { value: '', label: '全部状态' },
  { value: 'active', label: 'active' },
  { value: 'disabled', label: 'disabled' },
  { value: 'deleted', label: 'deleted' },
  { value: 'retracted', label: 'retracted' },
  { value: 'superseded', label: 'superseded' },
];

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

function downloadMemoryExport(payload: unknown) {
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `ainote-memories-${new Date().toISOString().slice(0, 10)}.json`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export default function MemoryControlPanel() {
  const addToast = useToastStore(state => state.addToast);
  const [memories, setMemories] = useState<MemoryRecord[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [request, setRequest] = useState<MemoryListParams>({ type: '', status: '', query: '' });
  const [typeFilter, setTypeFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [queryInput, setQueryInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busyMemoryId, setBusyMemoryId] = useState<number | null>(null);
  const [exporting, setExporting] = useState(false);
  const [previewQuery, setPreviewQuery] = useState('');
  const [previewNoteIds, setPreviewNoteIds] = useState('');
  const [preview, setPreview] = useState<ContextPreviewResponse | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [memoryEvents, setMemoryEvents] = useState<MemoryEventRecord[]>([]);
  const [eventsLoading, setEventsLoading] = useState(false);
  const [eventsError, setEventsError] = useState<string | null>(null);

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

  const loadMemoryEvents = useCallback(async () => {
    setEventsLoading(true);
    setEventsError(null);
    try {
      const response = await getMemoryEvents({ limit: 50 });
      setMemoryEvents(response.items);
    } catch (err) {
      const message = err instanceof Error ? err.message : '记忆事件加载失败';
      setEventsError(message);
      addToast({ type: 'failed', title: '记忆事件加载失败', message });
    } finally {
      setEventsLoading(false);
    }
  }, [addToast]);

  useEffect(() => {
    void loadMemoryEvents();
  }, [loadMemoryEvents]);

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

  const toggleMemoryStatus = async (memory: MemoryRecord) => {
    const nextStatus: MemoryStatus = memory.status === 'disabled' ? 'active' : 'disabled';
    setBusyMemoryId(memory.id);
    try {
      const updated = await updateMemory(memory.id, {
        status: nextStatus,
        reason: `user ${nextStatus === 'active' ? 'enabled' : 'disabled'} memory from control panel`,
      });
      setMemories(current => current.map(item => item.id === updated.id ? updated : item));
      void loadMemoryEvents();
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
      void loadMemoryEvents();
      addToast({ type: 'success', title: '记忆已删除' });
    } catch (err) {
      const message = err instanceof Error ? err.message : '记忆删除失败';
      addToast({ type: 'failed', title: '记忆删除失败', message });
    } finally {
      setBusyMemoryId(null);
    }
  };

  const handleExport = async () => {
    setExporting(true);
    try {
      const payload = await exportMemories();
      downloadMemoryExport(payload);
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

  return (
    <section className="memory-control-panel">
      <div className="memory-panel-header">
        <div>
          <h1 className="settings-page-title">AI 记忆</h1>
          <p className="settings-page-desc">
            查看、禁用、删除和导出长期记忆。删除或禁用后的记忆不会进入模型上下文。
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

      <section className="memory-events-panel" aria-label="记忆事件记录">
        <div className="memory-events-header">
          <div>
            <h2>事件记录</h2>
            <p>查看长期记忆的创建、强化、替换、删除和抽取失败记录，用于验证治理链路。</p>
          </div>
          <button
            type="button"
            className="memory-icon-button"
            onClick={() => void loadMemoryEvents()}
            aria-label="刷新记忆事件"
            title="刷新记忆事件"
            disabled={eventsLoading}
          >
            <ArrowPathIcon aria-hidden="true" />
          </button>
        </div>

        <div className="memory-events-summary" aria-live="polite">
          <span>{memoryEvents.length} 条事件</span>
          {eventsLoading && <span>加载中</span>}
          {eventsError && <span className="memory-error">{eventsError}</span>}
        </div>

        {memoryEvents.length === 0 && !eventsLoading ? (
          <p className="memory-empty">暂无记忆事件。新建、禁用、删除或纠正记忆后会出现在这里。</p>
        ) : (
          <ol className="memory-event-list">
            {memoryEvents.map(event => (
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
                    {event.memoryId !== null && (
                      <span className="memory-muted">Memory #{event.memoryId}</span>
                    )}
                  </div>
                  <dl className="memory-event-meta">
                    <div>
                      <dt>原因</dt>
                      <dd>{event.reason || 'n/a'}</dd>
                    </div>
                    <div>
                      <dt>Trace</dt>
                      <dd>{event.traceId || 'n/a'}</dd>
                    </div>
                    <div>
                      <dt>Actor</dt>
                      <dd>{event.actor || 'system'}</dd>
                    </div>
                  </dl>
                  {(event.beforeJson || event.afterJson) && (
                    <details className="memory-event-snapshot">
                      <summary>变更快照</summary>
                      {event.beforeJson && <pre>{event.beforeJson}</pre>}
                      {event.afterJson && <pre>{event.afterJson}</pre>}
                    </details>
                  )}
                </div>
              </li>
            ))}
          </ol>
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
                </div>
                <div className="memory-actions">
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
    </section>
  );
}
