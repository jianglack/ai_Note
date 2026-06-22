import { useEffect, useState, useCallback } from 'react';
import { getAgentMetrics } from '../../api';
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

export default function AgentMetricsDashboard({ onClose }: { onClose: () => void }) {
  const [metrics, setMetrics] = useState<MetricsData>(EMPTY_METRICS);
  const [loading, setLoading] = useState(false);

  const fetchMetrics = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getAgentMetrics();
      if (data && data.lastSampled) {
        setMetrics(data as MetricsData);
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
    <div className="amd-root">
      {/* Page header */}
      <div className="amd-page-header">
        <div style={{ flex: 1 }}>
          <div className="amd-breadcrumb">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"/><circle cx="12" cy="12" r="3"/></svg>
            <span>设置</span>
            <span className="amd-crumb-sep">›</span>
            <span>管理员</span>
            <span className="amd-crumb-sep">›</span>
            <span style={{ color: 'var(--color-text-secondary, #6b5848)' }}>Agent 运行指标</span>
          </div>
          <h1 className="amd-title">
            Agent 运行指标
            <span className="amd-title-underline" />
          </h1>
          <div className="amd-subtitle">
            智记 Agent 系统健康状态 · 最后采样于
            <span className="amd-mono" style={{ marginLeft: 4 }}>{metrics.lastSampled}</span>
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="amd-hbtn" onClick={fetchMetrics} disabled={loading}>
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/><path d="M21 3v5h-5"/><path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"/><path d="M3 21v-5h5"/></svg>
            {loading ? '刷新中…' : '刷新'}
          </button>
          <button type="button" className="amd-close-btn" aria-label="Close agent metrics dashboard" onClick={onClose}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"><path d="M18 6 6 18M6 6l12 12"/></svg>
            关闭
          </button>
        </div>
      </div>

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
