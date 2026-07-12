import { useEffect, useState, useCallback } from 'react';
import {
  getAgentMetrics,
  getMemoryEvents,
  getMemoryMetrics,
  type MemoryEventRecord,
  type MemoryMetricsSnapshot,
} from '../../api';
import { ToolWorkbenchShell } from '../workbench';
import './agent-metrics.css';

interface ToolMetric {
  name: string;
  calls: number;
  failures: number;
  rate: number;
  latency: number;
  lastFailure: string;
  desc: string;
}

interface MetricsData {
  planSuccessRate: number;
  plansCompleted: number;
  plansFailed: number;
  plansRolledBack: number;
  plansTotal: number;
  stepRetryRate: number;
  stepsTotal: number;
  stepsRetried: number;
  stepsSucceeded: number;
  stepsFailed: number;
  toolsCalls: number;
  toolsCount: number;
  compensationTotal: number;
  compensationVerified: number;
  compensationFailed: number;
  compensationRate: number;
  tools: ToolMetric[];
  lastSampled: string;
}

const EMPTY_METRICS: MetricsData = {
  planSuccessRate: 0, plansCompleted: 0, plansFailed: 0, plansRolledBack: 0, plansTotal: 0,
  stepRetryRate: 0, stepsTotal: 0, stepsRetried: 0, stepsSucceeded: 0, stepsFailed: 0,
  toolsCalls: 0, toolsCount: 0,
  compensationTotal: 0, compensationVerified: 0, compensationFailed: 0, compensationRate: 0,
  tools: [],
  lastSampled: '',
};

const EMPTY_MEMORY_METRICS: MemoryMetricsSnapshot = {
  status: 'normal',
  sampledAt: '',
  captureDecisionCount: 0,
  captureAllowedCount: 0,
  captureRejectedCount: 0,
  captureSkippedCount: 0,
  captureFailedCount: 0,
  captureRejectionRate: 0,
  captureCandidateCount: 0,
  captureWrittenMemoryCount: 0,
  memoryWriteCount: 0,
  memoryCreatedCount: 0,
  memoryReinforcedCount: 0,
  memorySupersededCount: 0,
  memoryWriteSkippedCount: 0,
  memoryWriteRate: 0,
  advisorRequestCount: 0,
  advisorFailureCount: 0,
  advisorUnavailableCount: 0,
  advisorSkippedCount: 0,
  advisorFailureRate: 0,
  userDeletionRequestCount: 0,
  userDeletedMemoryCount: 0,
  userDeletionRate: 0,
  retrievalRequestCount: 0,
  retrievalHitCount: 0,
  retrievalMissCount: 0,
  retrievalHitRate: 0,
  contextInjectionCount: 0,
  semanticInjectedMemoryCount: 0,
  episodicInjectedMemoryCount: 0,
  injectedMemoryCount: 0,
  averageInjectedMemories: 0,
  feedbackCount: 0,
  wrongWriteFeedbackCount: 0,
  wrongWriteFeedbackRate: 0,
  retentionPurgedMemoryCount: 0,
  captureP95LatencyMs: 0,
  retrievalP95LatencyMs: 0,
};

export default function AgentMetricsDashboard({ onClose }: { onClose: () => void }) {
  const [metrics, setMetrics] = useState<MetricsData>(EMPTY_METRICS);
  const [memoryMetrics, setMemoryMetrics] = useState<MemoryMetricsSnapshot>(EMPTY_MEMORY_METRICS);
  const [memoryEvents, setMemoryEvents] = useState<MemoryEventRecord[]>([]);
  const [loading, setLoading] = useState(false);

  const fetchMetrics = useCallback(async () => {
    setLoading(true);
    try {
      const [agentResult, memoryResult, memoryEventResult] = await Promise.allSettled([
        getAgentMetrics(),
        getMemoryMetrics(),
        getMemoryEvents({ limit: 30 }),
      ]);
      if (agentResult.status === 'fulfilled' && agentResult.value?.lastSampled) {
        setMetrics(agentResult.value as MetricsData);
      }
      if (memoryResult.status === 'fulfilled' && memoryResult.value?.sampledAt) {
        setMemoryMetrics(memoryResult.value);
      }
      if (memoryEventResult.status === 'fulfilled' && memoryEventResult.value?.items) {
        setMemoryEvents(memoryEventResult.value.items);
      }
    } catch {
      // Use default/previous metrics on error
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchMetrics();
    const interval = setInterval(fetchMetrics, 30000);
    return () => clearInterval(interval);
  }, [fetchMetrics]);

  return (
    <ToolWorkbenchShell
      title="Agent 指标"
      subtitle={`智记 Agent 系统健康状态 · 最后采样于 ${metrics.lastSampled || '暂无数据'}`}
      onBack={onClose}
      backLabel="关闭 Agent 指标"
      closeOnEscape
      secondaryActions={[{
        key: 'refresh',
        label: loading ? '刷新中' : '刷新',
        disabled: loading,
        onClick: () => { void fetchMetrics(); },
      }]}
    >
    <div className="amd-root">
      <div className="amd-content">
        {/* Index cards */}
        <div className="amd-cards-row">
          <IndexCard label="计划成功率" value={metrics.planSuccessRate.toFixed(1)} unit="%"
            sub={`${metrics.plansCompleted} 完成 · ${metrics.plansFailed} 失败 · ${metrics.plansRolledBack} 回滚`}
            accent="sage" status={metrics.planSuccessRate >= 70 ? '正常' : '注意'} />
          <IndexCard label="步骤重试率" value={metrics.stepRetryRate.toFixed(1)} unit="%"
            sub={`${metrics.stepsTotal} 次执行 · ${metrics.stepsRetried} 次重试`}
            accent="gold" status={metrics.stepRetryRate < 15 ? '正常' : '注意'} />
          <IndexCard label="工具总调用" value={String(metrics.toolsCalls)}
            sub={`${metrics.toolsCount} 个工具 · 近 24h`}
            accent="bluegray" />
          <IndexCard label="补偿验证率" value={metrics.compensationRate.toFixed(1)} unit="%"
            sub={`${metrics.compensationTotal} 次执行 · ${metrics.compensationVerified} 已验证`}
            accent="rust" status={metrics.compensationRate >= 80 ? '正常' : '注意'} />
        </div>

        <MemoryMetricsPanel metrics={memoryMetrics} events={memoryEvents} />

        {/* Tools table */}
        <ToolsTable tools={metrics.tools} />

        {/* Detail sections */}
        <div className="amd-detail-grid">
          <DetailsSection title="计划 · Plans" items={[
            { label: '已创建', value: String(metrics.plansTotal) },
            { label: '已完成', value: String(metrics.plansCompleted), color: 'var(--color-sage, #6b7a5a)' },
            { label: '失败', value: String(metrics.plansFailed), color: 'var(--color-accent, #b8452e)' },
            { label: '已回滚', value: String(metrics.plansRolledBack), color: '#8c7333' },
          ]} />
          <DetailsSection title="步骤 · Steps" items={[
            { label: '执行', value: String(metrics.stepsTotal) },
            { label: '成功', value: String(metrics.stepsSucceeded), color: 'var(--color-sage, #6b7a5a)' },
            { label: '失败', value: String(metrics.stepsFailed), color: 'var(--color-accent, #b8452e)' },
            { label: '重试', value: String(metrics.stepsRetried), color: '#8c7333' },
          ]} />
        </div>

        <DetailsSection title="补偿 · Compensation" items={[
          { label: '触发次数', value: String(metrics.compensationTotal), sub: '由失败步骤激活' },
          { label: '已验证', value: String(metrics.compensationVerified), color: 'var(--color-sage, #6b7a5a)', sub: '状态符合预期' },
          { label: '验证失败', value: String(metrics.compensationFailed), color: 'var(--color-accent, #b8452e)', sub: '待人工审' },
          { label: '验证率', value: metrics.compensationRate.toFixed(1), unit: '%', color: metrics.compensationRate >= 80 ? 'var(--color-sage, #6b7a5a)' : 'var(--color-accent, #b8452e)', sub: metrics.compensationRate >= 80 ? '正常' : '⚠ 低于阈值 80%' },
        ]} />

        {/* Alert banner — show if any tool has high failure rate */}
        {metrics.tools.some(t => t.rate >= 10) && (
          <div className="amd-alert">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--color-accent, #b8452e)" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
              <path d="M12 9v4M12 17h.01"/><path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"/>
            </svg>
            <span>
              <b>{metrics.tools.filter(t => t.rate >= 10).length} 项异常需关注：</b>{' '}
              {metrics.tools.filter(t => t.rate >= 10).map(t => `${t.name} 失败率 ${t.rate.toFixed(1)}%`).join('；')}
            </span>
          </div>
        )}

        <div className="amd-footer-text">
          智记 ADMIN · Agent 运行指标 · 30s 自动刷新
        </div>
      </div>
    </div>
    </ToolWorkbenchShell>
  );
}

/* ── Sub-components ── */

function IndexCard({ label, value, unit, sub, accent, status }: {
  label: string; value: string; unit?: string; sub: string;
  accent: 'sage' | 'gold' | 'bluegray' | 'rust'; status?: string;
}) {
  return (
    <div className={`amd-index-card ${accent}`}>
      <div className="amd-ic-head">
        <div className={`amd-ic-icon ${accent}`}>
          {accent === 'sage' && <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M20 6 9 17l-5-5"/></svg>}
          {accent === 'gold' && <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M3 7v6h6"/><path d="M3 13a9 9 0 1 0 3-7"/></svg>}
          {accent === 'bluegray' && <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/></svg>}
          {accent === 'rust' && <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>}
        </div>
        <span className="amd-ic-label">{label}</span>
        {status && <span className={`amd-ic-status ${accent}`}>{status}</span>}
      </div>
      <div className="amd-ic-value-row">
        <span className="amd-ic-value">{value}</span>
        {unit && <span className="amd-ic-unit">{unit}</span>}
      </div>
      <div className="amd-ic-sub">{sub}</div>
      <Sparkline accent={accent} />
    </div>
  );
}

function Sparkline({ accent }: { accent: string }) {
  const data: Record<string, number[]> = {
    sage: [62, 68, 71, 69, 74, 78, 82, 80, 83, 85, 83],
    gold: [8, 6, 9, 11, 14, 13, 11, 10, 12, 11, 11.1],
    bluegray: [80, 95, 100, 110, 118, 128, 135, 140, 148, 152, 156],
    rust: [70, 60, 55, 50, 55, 52, 48, 50, 48, 52, 50],
  };
  const d = data[accent] || data.sage;
  const w = 200, h = 26;
  const max = Math.max(...d), min = Math.min(...d);
  const norm = (v: number) => h - ((v - min) / (max - min || 1)) * (h - 4) - 2;
  const pts = d.map((v, i) => `${(i / (d.length - 1)) * w},${norm(v)}`).join(' ');

  return (
    <svg viewBox={`0 0 ${w} ${h}`} preserveAspectRatio="none" className="amd-sparkline">
      <polyline points={pts} fill="none" stroke="currentColor" strokeWidth={1.4} strokeLinecap="round" strokeLinejoin="round" opacity={0.85} />
      <circle cx={w} cy={norm(d[d.length - 1])} r={2.2} fill="currentColor" />
    </svg>
  );
}

function MemoryMetricsPanel({ metrics, events }: { metrics: MemoryMetricsSnapshot; events: MemoryEventRecord[] }) {
  return (
    <section className="amd-memory-panel">
      <div className="amd-table-header">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" style={{ color: 'var(--color-text-secondary, #6b5848)' }}>
          <path d="M4 19V5" /><path d="M4 19h16" /><path d="m8 15 3-3 3 2 4-6" />
        </svg>
        <span className="amd-table-title">记忆生产观测 · Memory</span>
        <span className={`amd-memory-status ${metrics.status === 'normal' ? 'ok' : 'warn'}`}>
          {metrics.status === 'normal' ? '正常' : '注意'}
        </span>
        <span style={{ flex: 1 }} />
        <span className="amd-mono" style={{ fontSize: 10.5 }}>
          GET /api/admin/memory-metrics · {formatSampledAt(metrics.sampledAt)}
        </span>
      </div>

      <div className="amd-memory-body">
        <div className="amd-cards-row">
          <IndexCard label="记忆写入率" value={metrics.memoryWriteRate.toFixed(1)} unit="%"
            sub={`${metrics.memoryWriteCount} 写入 · ${metrics.captureDecisionCount} 次 capture`}
            accent="sage" status={metrics.memoryWriteRate > 0 ? '活跃' : '待采样'} />
          <IndexCard label="拒绝率" value={metrics.captureRejectionRate.toFixed(1)} unit="%"
            sub={`${metrics.captureRejectedCount} 拒绝 · ${metrics.captureSkippedCount} 跳过 · ${metrics.captureFailedCount} 失败`}
            accent="gold" status={metrics.captureRejectionRate <= 80 ? '正常' : '注意'} />
          <IndexCard label="召回命中率" value={metrics.retrievalHitRate.toFixed(1)} unit="%"
            sub={`${metrics.retrievalHitCount} 命中 · ${metrics.retrievalMissCount} 未命中`}
            accent="bluegray" status={metrics.retrievalHitRate > 0 ? '有命中' : '待采样'} />
          <IndexCard label="Advisor 失败率" value={metrics.advisorFailureRate.toFixed(1)} unit="%"
            sub={`${metrics.advisorFailureCount} 失败 · ${metrics.advisorUnavailableCount} 不可用 · ${metrics.advisorSkippedCount} 跳过`}
            accent="rust" status={metrics.advisorFailureRate <= 5 ? '正常' : '注意'} />
        </div>

        <div className="amd-detail-grid">
          <DetailsSection title="写入与拒绝 · Capture" items={[
            { label: '允许', value: String(metrics.captureAllowedCount), color: 'var(--color-sage, #6b7a5a)' },
            { label: '候选', value: String(metrics.captureCandidateCount) },
            { label: '写入', value: String(metrics.captureWrittenMemoryCount), color: 'var(--color-sage, #6b7a5a)' },
            { label: '跳过写入', value: String(metrics.memoryWriteSkippedCount), color: '#8c7333' },
          ]} />
          <DetailsSection title="召回与注入 · Retrieval" items={[
            { label: '请求', value: String(metrics.retrievalRequestCount) },
            { label: '注入事件', value: String(metrics.contextInjectionCount) },
            { label: '注入记忆', value: String(metrics.injectedMemoryCount), color: 'var(--color-sage, #6b7a5a)' },
            { label: '平均注入', value: metrics.averageInjectedMemories.toFixed(1) },
          ]} />
        </div>

        <div className="amd-detail-grid">
          <DetailsSection title="反馈与删除 · Feedback" items={[
            { label: '反馈', value: String(metrics.feedbackCount) },
            { label: '错记反馈', value: String(metrics.wrongWriteFeedbackCount), color: metrics.wrongWriteFeedbackCount > 0 ? 'var(--color-accent, #b8452e)' : 'var(--color-sage, #6b7a5a)' },
            { label: '错记率', value: metrics.wrongWriteFeedbackRate.toFixed(1), unit: '%' },
            { label: '用户删除', value: String(metrics.userDeletionRequestCount), color: '#8c7333' },
          ]} />
          <DetailsSection title="延迟与保留 · Latency" items={[
            { label: 'Capture p95', value: metrics.captureP95LatencyMs.toFixed(1), unit: 'ms' },
            { label: 'Retrieval p95', value: metrics.retrievalP95LatencyMs.toFixed(1), unit: 'ms' },
            { label: '删除记忆数', value: String(metrics.userDeletedMemoryCount) },
            { label: '保留期清理', value: String(metrics.retentionPurgedMemoryCount) },
          ]} />
        </div>

        <MemoryEventEvidence events={events} />
      </div>
    </section>
  );
}

function MemoryEventEvidence({ events }: { events: MemoryEventRecord[] }) {
  return (
    <div className="amd-memory-events">
      <div className="amd-events-header">
        <span className="amd-events-title">最近变化原因 · Evidence</span>
        <span className="amd-mono">GET /api/memories/events · {events.length} 条</span>
      </div>
      {events.length === 0 ? (
        <div className="amd-events-empty">暂无记忆审计事件</div>
      ) : (
        <div className="amd-events-list">
          {events.slice(0, 12).map(event => {
            const detail = describeMemoryEvent(event);
            return (
              <div key={event.id} className={`amd-event-row ${detail.severity}`}>
                <div className="amd-event-main">
                  <span className="amd-event-type">{event.eventType}</span>
                  <span className="amd-event-result">{detail.result}</span>
                  <span className="amd-mono">{formatSampledAt(event.createdAt || '')}</span>
                  {event.memoryId != null && <span className="amd-event-memory">memory #{event.memoryId}</span>}
                </div>
                <div className="amd-event-summary">{detail.summary}</div>
                <div className="amd-event-meta">
                  <span>reason: {detail.reason || 'n/a'}</span>
                  {detail.decisionType && <span>decision: {detail.decisionType}</span>}
                  {detail.signals.length > 0 && <span>signals: {detail.signals.join(', ')}</span>}
                  {event.traceId && <span>trace: {event.traceId}</span>}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

function ToolsTable({ tools }: { tools: ToolMetric[] }) {
  return (
    <div className="amd-table">
      <div className="amd-table-header">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" style={{ color: 'var(--color-text-secondary, #6b5848)' }}>
          <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>
        </svg>
        <span className="amd-table-title">工具调用 · 近 24 小时</span>
        <span className="amd-table-count">{tools.length} 个工具</span>
        <span style={{ flex: 1 }} />
        <span className="amd-mono" style={{ fontSize: 10.5 }}>GET /api/admin/agent-metrics · 30s 自动刷新</span>
      </div>

      <div className="amd-table-cols">
        <div>工具名称</div>
        <div style={{ textAlign: 'right' }}>调用</div>
        <div style={{ textAlign: 'right' }}>失败率</div>
        <div style={{ textAlign: 'right' }}>P50 延迟</div>
        <div style={{ textAlign: 'right' }}>上次失败</div>
      </div>

      {tools.length === 0 ? (
        <div style={{ padding: '24px 18px', textAlign: 'center', color: 'var(--color-text-tertiary, #9a8770)', fontSize: 13 }}>
          暂无工具调用记录 · 使用 AI 对话后数据将自动出现
        </div>
      ) : tools.map((t, i) => {
        const high = t.rate >= 10;
        return (
          <div key={t.name} className={`amd-table-row${high ? ' alert' : ''}`}
            style={{ borderBottom: i < tools.length - 1 ? '1px dashed rgba(43,38,32,0.10)' : 'none' }}>
            {high && <div className="amd-row-bar" />}
            <div>
              <div className="amd-tool-name">{t.name}</div>
              <div className="amd-tool-desc">{t.desc}</div>
            </div>
            <div style={{ textAlign: 'right' }}>
              <span className="amd-tool-calls">{t.calls}</span>
              <span className="amd-tool-calls-unit">次</span>
            </div>
            <div style={{ textAlign: 'right' }}>
              <RatePill rate={t.rate} />
              <div className="amd-mono" style={{ fontSize: 10, marginTop: 2 }}>
                {t.failures} / {t.calls}
              </div>
            </div>
            <div style={{ textAlign: 'right' }}>
              <span className="amd-mono" style={{
                fontSize: 12.5,
                color: t.latency > 1000 ? 'var(--color-accent, #b8452e)' : 'var(--color-text, #2b2620)',
              }}>
                {t.latency >= 1000 ? (t.latency / 1000).toFixed(2) + 's' : t.latency + 'ms'}
              </span>
              <LatencyBar ms={t.latency} />
            </div>
            <div style={{ textAlign: 'right' }}>
              <span className="amd-mono" style={{
                fontSize: 11.5,
                color: t.lastFailure === '从未' ? 'var(--color-sage, #6b7a5a)' : 'var(--color-text-secondary, #6b5848)',
              }}>{t.lastFailure}</span>
            </div>
          </div>
        );
      })}
    </div>
  );
}

function formatSampledAt(value: string) {
  if (!value) return '暂无数据';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('zh-CN', { hour12: false });
}

function describeMemoryEvent(event: MemoryEventRecord): {
  result: string;
  summary: string;
  reason: string;
  decisionType: string;
  signals: string[];
  severity: 'normal' | 'warn' | 'danger';
} {
  const after = parseJsonRecord(event.afterJson);
  const metadata = parseNestedJsonRecord(after.metadata);
  const reason = stringValue(event.reason) || stringValue(after.reason) || stringValue(metadata.policy_reason);
  const decisionType = stringValue(after.decisionType) || stringValue(metadata.decision_type);
  const signals = arrayOfStrings(after.matchedSignals).length > 0
    ? arrayOfStrings(after.matchedSignals)
    : arrayOfStrings(metadata.policy_signals);
  const content = stringValue(after.content)
    || stringValue(after.userMessagePreview)
    || stringValue(metadata.content)
    || '';
  const status = stringValue(after.status) || event.eventType.toLowerCase();
  const privacyFindings = parseFindingSummary(after.privacyFindings);
  const summaryParts = [
    content ? `内容：${content}` : '',
    privacyFindings ? `隐私：${privacyFindings}` : '',
  ].filter(Boolean);
  const summary = summaryParts.length > 0 ? summaryParts.join(' · ') : '无内容摘要';
  const severity = event.eventType.includes('REJECTED') || event.eventType.includes('FAILED')
    ? 'danger'
    : event.eventType.includes('DELETED') || event.eventType.includes('SUPERSEDED') || event.eventType.includes('SKIPPED')
      ? 'warn'
      : 'normal';

  return {
    result: status,
    summary,
    reason,
    decisionType,
    signals,
    severity,
  };
}

function parseJsonRecord(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'string') return {};
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? parsed as Record<string, unknown>
      : {};
  } catch {
    return {};
  }
}

function parseNestedJsonRecord(value: unknown): Record<string, unknown> {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    return value as Record<string, unknown>;
  }
  return parseJsonRecord(value);
}

function stringValue(value: unknown): string {
  return typeof value === 'string' ? value : '';
}

function arrayOfStrings(value: unknown): string[] {
  return Array.isArray(value) ? value.filter(item => typeof item === 'string') : [];
}

function parseFindingSummary(value: unknown): string {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return '';
  return Object.entries(value as Record<string, unknown>)
    .filter(([, count]) => typeof count === 'number' && count > 0)
    .map(([kind, count]) => `${kind}×${count}`)
    .join(', ');
}

function RatePill({ rate }: { rate: number }) {
  const cls = rate >= 10 ? 'high' : rate >= 5 ? 'mid' : 'low';
  return (
    <span className={`amd-rate-pill ${cls}`}>
      {rate >= 10 && '● '}{rate.toFixed(1)}%
    </span>
  );
}

function LatencyBar({ ms }: { ms: number }) {
  const pct = Math.min(100, (ms / 2000) * 100);
  const cls = ms > 1000 ? 'high' : ms > 400 ? 'mid' : 'low';
  return (
    <div className="amd-latency-track">
      <div className={`amd-latency-fill ${cls}`} style={{ width: `${pct}%` }} />
    </div>
  );
}

function DetailsSection({ title, items }: {
  title: string;
  items: { label: string; value: string; unit?: string; color?: string; sub?: string }[];
}) {
  const [open, setOpen] = useState(true);
  return (
    <div className="amd-detail-section">
      <button type="button" className="amd-detail-head" aria-expanded={open} onClick={() => setOpen(!open)}>
        <span className="amd-detail-title">{title}</span>
        <span style={{ flex: 1 }} />
        <span className="amd-detail-chevron">{open ? '▾' : '▸'}</span>
      </button>
      {open && (
        <div className="amd-detail-body" style={{ gridTemplateColumns: `repeat(${items.length}, 1fr)` }}>
          {items.map((it, i) => (
            <div key={i} className="amd-detail-cell"
              style={{ borderRight: i < items.length - 1 ? '1px dashed rgba(43,38,32,0.12)' : 'none' }}>
              <div className="amd-detail-cell-label">{it.label}</div>
              <div className="amd-detail-cell-value" style={{ color: it.color }}>
                {it.value}
                {it.unit && <span className="amd-detail-cell-unit">{it.unit}</span>}
              </div>
              {it.sub && <div className="amd-detail-cell-sub">{it.sub}</div>}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
