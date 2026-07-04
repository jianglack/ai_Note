import { useCallback, useEffect, useMemo, useState } from 'react';
import { clearChatMemory, getAgentTraces, getTraceStats } from '../api';
import { askConfirm, showAlert } from '../services/dialogService';
import type { AgentTrace, TraceStats } from '../api';
import {
  ToolControlRail,
  ToolDetailPanel,
  ToolWorkbenchShell,
} from './workbench';
import './TracesPanel.css';

interface TracesPanelProps {
  onClose: () => void;
}

function formatTime(dateStr: string): string {
  const d = new Date(dateStr.replace(' ', 'T'));
  return d.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

function formatLatency(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
}

function parseToolsCalled(json: string | null): string[] {
  if (!json) return [];
  try {
    const tools = JSON.parse(json);
    return tools.map((tool: { name: string }) => tool.name);
  } catch {
    return [];
  }
}

export default function TracesPanel({ onClose }: TracesPanelProps) {
  const [traces, setTraces] = useState<AgentTrace[]>([]);
  const [stats, setStats] = useState<TraceStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedTrace, setSelectedTrace] = useState<AgentTrace | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [tracesData, statsData] = await Promise.all([
        getAgentTraces(50),
        getTraceStats(),
      ]);
      setTraces(tracesData);
      setStats(statsData);
      setSelectedTrace((current) => {
        if (!current) return null;
        return tracesData.find((trace) => trace.id === current.id) ?? null;
      });
    } catch (err) {
      const message = err instanceof Error ? err.message : '调用追踪加载失败';
      setError(message);
      setTraces([]);
      setStats(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  async function handleClearMemory() {
    if (!await askConfirm({
      title: 'Clear chat memory',
      message: 'This resets the AI conversation context.',
      confirmLabel: 'Clear',
      danger: true,
    })) return;

    try {
      await clearChatMemory();
      await showAlert({ title: 'Memory cleared' });
    } catch (err) {
      const message = err instanceof Error ? err.message : '清除记忆失败';
      setError(message);
    }
  }

  const selectedTools = useMemo(() => parseToolsCalled(selectedTrace?.toolsCalled ?? null), [selectedTrace]);

  return (
    <ToolWorkbenchShell
      title="调用追踪"
      subtitle="查看 Agent 请求、工具调用、Token 和错误"
      onBack={onClose}
      backLabel="关闭调用追踪"
      closeOnEscape
      mainClassName="traces-workbench-main"
      secondaryActions={[
        { key: 'clear-memory', label: '清除记忆', variant: 'danger', onClick: () => { void handleClearMemory(); } },
        { key: 'refresh', label: loading ? '刷新中' : '刷新', disabled: loading, onClick: () => { void loadData(); } },
      ]}
      leftRail={(
        <ToolControlRail
          sections={[
            {
              key: 'summary',
              title: '统计',
              content: stats ? (
                <div className="traces-rail-stats">
                  <span><strong>{stats.todayCalls}</strong> 今日调用</span>
                  <span><strong>{stats.todayTokens.toLocaleString()}</strong> 今日 Token</span>
                  <span><strong>{stats.totalCalls}</strong> 总调用</span>
                  <span><strong>{stats.totalTokens.toLocaleString()}</strong> 总 Token</span>
                </div>
              ) : (
                <div className="traces-rail-empty">暂无统计</div>
              ),
            },
            {
              key: 'scope',
              title: '范围',
              content: <div className="traces-rail-empty">最近 50 条调用</div>,
            },
          ]}
        />
      )}
      detailPanel={(
        <ToolDetailPanel
          title="调用详情"
          subtitle={selectedTrace?.status === 'ERROR' ? '错误' : selectedTrace?.status}
          empty={!selectedTrace}
          emptyMessage="选择一条调用记录查看输入、输出和工具。"
        >
          {selectedTrace && (
            <div className="trace-detail">
              <div className="detail-row">
                <span className="detail-label">模型</span>
                <span className="detail-value">{selectedTrace.model || '-'}</span>
              </div>
              <div className="detail-row">
                <span className="detail-label">Token</span>
                <span className="detail-value">
                  输入 {selectedTrace.inputTokens} + 输出 {selectedTrace.outputTokens} = {selectedTrace.totalTokens}
                </span>
              </div>
              {selectedTools.length > 0 && (
                <div className="detail-row">
                  <span className="detail-label">工具</span>
                  <span className="detail-value">{selectedTools.join(', ')}</span>
                </div>
              )}
              {selectedTrace.outputText && (
                <div className="detail-row detail-col">
                  <span className="detail-label">输出</span>
                  <span className="detail-value detail-text">
                    {selectedTrace.outputText.length > 200 ? `${selectedTrace.outputText.slice(0, 200)}...` : selectedTrace.outputText}
                  </span>
                </div>
              )}
              {selectedTrace.errorMessage && (
                <div className="detail-row detail-col">
                  <span className="detail-label">错误</span>
                  <span className="detail-value detail-error">{selectedTrace.errorMessage}</span>
                </div>
              )}
            </div>
          )}
        </ToolDetailPanel>
      )}
    >
      <div className="traces-content">
        {stats && (
          <div className="traces-stats" aria-label="调用统计">
            <div className="stat-card">
              <div className="stat-value">{stats.todayCalls}</div>
              <div className="stat-label">今日调用</div>
            </div>
            <div className="stat-card">
              <div className="stat-value">{stats.todayTokens.toLocaleString()}</div>
              <div className="stat-label">今日 Token</div>
            </div>
            <div className="stat-card">
              <div className="stat-value">{stats.totalCalls}</div>
              <div className="stat-label">总调用</div>
            </div>
            <div className="stat-card">
              <div className="stat-value">{stats.totalTokens.toLocaleString()}</div>
              <div className="stat-label">总 Token</div>
            </div>
          </div>
        )}

        {error && <div className="traces-error-banner" role="alert">{error}</div>}

        {loading ? (
          <div className="traces-loading">加载中...</div>
        ) : traces.length === 0 ? (
          <div className="traces-empty">暂无调用记录。发送一条需要工具执行的消息（如"帮我创建一个笔记"）来生成追踪数据。</div>
        ) : (
          <div className="traces-list">
            {traces.map((trace) => (
              <button
                key={trace.id}
                type="button"
                className={`trace-item ${trace.status === 'ERROR' ? 'trace-error' : ''} ${selectedTrace?.id === trace.id ? 'trace-selected' : ''}`}
                aria-pressed={selectedTrace?.id === trace.id}
                aria-label={`Select trace ${trace.inputText || trace.id}`}
                onClick={() => setSelectedTrace(trace)}
              >
                <span className={`trace-status ${trace.status === 'ERROR' ? 'status-error' : 'status-ok'}`}>
                  {trace.status === 'ERROR' ? 'ERR' : 'OK'}
                </span>
                <span className="trace-input" title={trace.inputText}>
                  {trace.inputText?.length > 56 ? `${trace.inputText.slice(0, 56)}...` : trace.inputText}
                </span>
                <span className="trace-meta">
                  {formatLatency(trace.latencyMs)} | {trace.totalTokens} tok
                </span>
                <span className="trace-time">{formatTime(trace.createdAt)}</span>
              </button>
            ))}
          </div>
        )}
      </div>
    </ToolWorkbenchShell>
  );
}
