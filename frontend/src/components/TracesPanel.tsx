import { useState, useEffect } from 'react';
import { getAgentTraces, getTraceStats, clearChatMemory } from '../api';
import { askConfirm, showAlert } from '../services/dialogService';
import type { AgentTrace, TraceStats } from '../api';
import './TracesPanel.css';

interface TracesPanelProps {
  onClose: () => void;
}

function formatTime(dateStr: string): string {
  const d = new Date(dateStr.replace(' ', 'T'));
  return d.toLocaleString('zh-CN', {
    month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit'
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
    return tools.map((t: { name: string }) => t.name);
  } catch {
    return [];
  }
}

export default function TracesPanel({ onClose }: TracesPanelProps) {
  const [traces, setTraces] = useState<AgentTrace[]>([]);
  const [stats, setStats] = useState<TraceStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [selectedTrace, setSelectedTrace] = useState<AgentTrace | null>(null);

  useEffect(() => {
    loadData();
  }, []);

  async function loadData() {
    setLoading(true);
    try {
      const [tracesData, statsData] = await Promise.all([
        getAgentTraces(50),
        getTraceStats()
      ]);
      setTraces(tracesData);
      setStats(statsData);
    } catch (err) {
      console.error('Failed to load traces:', err);
    } finally {
      setLoading(false);
    }
  }

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
      console.error('Failed to clear memory:', err);
    }
  }

  return (
    <div className="traces-overlay" onClick={onClose}>
      <div className="traces-panel" onClick={(e) => e.stopPropagation()}>
        <div className="traces-header">
          <h2>Agent 调用追踪</h2>
          <div className="traces-header-actions">
            <button className="traces-btn-secondary" onClick={handleClearMemory}>
              清除记忆
            </button>
            <button className="traces-btn-secondary" onClick={loadData}>
              刷新
            </button>
            <button type="button" className="traces-close" aria-label="Close traces panel" onClick={onClose}>x</button>
          </div>
        </div>

        {/* 统计卡片 */}
        {stats && (
          <div className="traces-stats">
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

        {/* 追踪列表 */}
        <div className="traces-content">
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
                  aria-expanded={selectedTrace?.id === trace.id}
                  aria-label={`Toggle trace ${trace.inputText || trace.id}`}
                  onClick={() => setSelectedTrace(selectedTrace?.id === trace.id ? null : trace)}
                >
                  <div className="trace-row">
                    <span className={`trace-status ${trace.status === 'ERROR' ? 'status-error' : 'status-ok'}`}>
                      {trace.status === 'ERROR' ? 'ERR' : 'OK'}
                    </span>
                    <span className="trace-input" title={trace.inputText}>
                      {trace.inputText?.length > 40 ? trace.inputText.slice(0, 40) + '...' : trace.inputText}
                    </span>
                    <span className="trace-meta">
                      {formatLatency(trace.latencyMs)} | {trace.totalTokens} tok
                    </span>
                    <span className="trace-time">{formatTime(trace.createdAt)}</span>
                  </div>

                  {/* 展开详情 */}
                  {selectedTrace?.id === trace.id && (
                    <div className="trace-detail">
                      <div className="detail-row">
                        <span className="detail-label">模型</span>
                        <span className="detail-value">{trace.model || '-'}</span>
                      </div>
                      <div className="detail-row">
                        <span className="detail-label">Token</span>
                        <span className="detail-value">
                          输入 {trace.inputTokens} + 输出 {trace.outputTokens} = {trace.totalTokens}
                        </span>
                      </div>
                      {parseToolsCalled(trace.toolsCalled).length > 0 && (
                        <div className="detail-row">
                          <span className="detail-label">工具</span>
                          <span className="detail-value">
                            {parseToolsCalled(trace.toolsCalled).join(', ')}
                          </span>
                        </div>
                      )}
                      {trace.outputText && (
                        <div className="detail-row detail-col">
                          <span className="detail-label">输出</span>
                          <span className="detail-value detail-text">
                            {trace.outputText.length > 200 ? trace.outputText.slice(0, 200) + '...' : trace.outputText}
                          </span>
                        </div>
                      )}
                      {trace.errorMessage && (
                        <div className="detail-row detail-col">
                          <span className="detail-label">错误</span>
                          <span className="detail-value detail-error">{trace.errorMessage}</span>
                        </div>
                      )}
                    </div>
                  )}
                </button>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
