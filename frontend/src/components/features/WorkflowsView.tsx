import { useState, useEffect, useRef } from 'react';
import { getWorkflows, createWorkflow as apiCreateWorkflow, deleteWorkflow, toggleWorkflow, runWorkflow, getWorkflowRuns } from '../../api';
import { askConfirm } from '../../services/dialogService';
import './features.css';

interface Workflow {
  id: string;
  name: string;
  description: string;
  triggerType: string;
  triggerConfig: string;
  steps: string;
  enabled: boolean;
  lastRunAt: string | null;
  createdAt: string;
}

interface WorkflowRun {
  id: string;
  workflowId: string;
  status: string;
  startedAt: string;
  completedAt?: string;
  results?: string;
  error?: string;
}

export default function WorkflowsView({ onClose }: { onClose: () => void }) {
  const [tab, setTab] = useState<'list' | 'history'>('list');
  const [flows, setFlows] = useState<Workflow[]>([]);
  const [runs, setRuns] = useState<WorkflowRun[]>([]);
  const [selectedWf, setSelectedWf] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [showResult, setShowResult] = useState<WorkflowRun | null>(null);
  const [form, setForm] = useState({ name: '', description: '', triggerType: 'manual', steps: '' });
  const [runningIds, setRunningIds] = useState<Set<string>>(new Set());
  const pollIntervalsRef = useRef<Map<string, ReturnType<typeof setInterval>>>(new Map());

  useEffect(() => { loadFlows(); }, []);
  useEffect(() => () => {
    pollIntervalsRef.current.forEach(interval => clearInterval(interval));
    pollIntervalsRef.current.clear();
  }, []);

  const loadFlows = async () => {
    try { setFlows(await getWorkflows()); } catch (e) { console.error(e); }
  };

  const loadRuns = async (wfId: string) => {
    try { setRuns(await getWorkflowRuns(wfId)); setSelectedWf(wfId); setTab('history'); } catch (e) { console.error(e); }
  };

  const handleCreate = async () => {
    // 将用户输入的多行步骤转为 JSON 数组
    const stepsArray = form.steps
      .split('\n')
      .map(line => line.trim())
      .filter(line => line.length > 0)
      .map(instruction => JSON.stringify({ type: 'ai_chat', instruction }));
    const stepsJson = '[' + stepsArray.join(',') + ']';

    try {
      await apiCreateWorkflow(form.name, form.triggerType, '{}', stepsJson);
      setShowCreate(false);
      setForm({ name: '', description: '', triggerType: 'manual', steps: '' });
      loadFlows();
    } catch (e) { console.error(e); }
  };

  const handleToggle = async (id: string) => {
    try { await toggleWorkflow(id); loadFlows(); } catch (e) { console.error(e); }
  };

  const handleRun = async (id: string) => {
    try {
      setRunningIds(prev => new Set(prev).add(id));
      await runWorkflow(id);
      // Poll for completion
      pollRunStatus(id);
    } catch (e) { console.error(e); }
  };

  const pollRunStatus = (wfId: string) => {
    const existing = pollIntervalsRef.current.get(wfId);
    if (existing) {
      clearInterval(existing);
    }
    let attempts = 0;
    const interval = setInterval(async () => {
      attempts++;
      try {
        const r = await getWorkflowRuns(wfId);
        const latest = r[0];
        if (latest && (latest.status === 'completed' || latest.status === 'failed' || attempts > 60)) {
          clearInterval(interval);
          pollIntervalsRef.current.delete(wfId);
          setRunningIds(prev => { const n = new Set(prev); n.delete(wfId); return n; });
          loadFlows();
        }
      } catch {
        clearInterval(interval);
        pollIntervalsRef.current.delete(wfId);
        setRunningIds(prev => { const n = new Set(prev); n.delete(wfId); return n; });
      }
    }, 3000);
    pollIntervalsRef.current.set(wfId, interval);
  };

  const handleDelete = async (id: string) => {
    const confirmed = await askConfirm({
      title: '删除工作流',
      message: '确定删除此工作流？',
      confirmLabel: '删除',
      danger: true,
    });
    if (!confirmed) return;
    try { await deleteWorkflow(id); loadFlows(); } catch (e) { console.error(e); }
  };

  const parseSteps = (stepsJson: string): string[] => {
    try {
      const arr = JSON.parse(stepsJson);
      return arr.map((s: any) => s.instruction || s.type || '未知步骤');
    } catch { return []; }
  };

  const parseResults = (resultsJson?: string): any[] => {
    if (!resultsJson) return [];
    try { return JSON.parse(resultsJson); } catch { return []; }
  };

  const trigBadge: Record<string, { label: string; ico: string; cls: string }> = {
    schedule: { label: '定时', ico: '◷', cls: 'info' },
    event: { label: '事件', ico: '⚡', cls: 'warn' },
    manual: { label: '手动', ico: '✋', cls: '' },
  };

  return (
    <div className="wf-root">
      <div className="wf-head">
        <div>
          <h1 className="wf-title">AI 工作流</h1>
          <p style={{ margin: '4px 0 0', fontSize: 13, color: 'var(--color-text-secondary)' }}>
            定义多步 AI 任务，自动依次执行 · 共 {flows.length} 个工作流
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="feat-btn ghost" onClick={onClose}>← 返回</button>
          <button className="feat-btn primary" onClick={() => setShowCreate(true)}>+ 创建工作流</button>
        </div>
      </div>

      <div className="wf-tabs">
        <button className={tab === 'list' ? 'on' : ''} onClick={() => setTab('list')}>我的工作流</button>
        <button className={tab === 'history' ? 'on' : ''} onClick={() => setTab('history')}>运行历史</button>
      </div>

      {tab === 'list' && (
        <div className="wf-grid">
          {flows.map(f => {
            const trig = trigBadge[f.triggerType] || trigBadge.manual;
            const steps = parseSteps(f.steps);
            const isRunning = runningIds.has(f.id);
            return (
              <div key={f.id} className={`wf-card ${f.enabled ? '' : 'off'}`}>
                <div className="wf-card-top">
                  <span className="wf-card-name">{f.name}</span>
                  <button className={`feat-toggle ${f.enabled ? 'on' : ''}`} onClick={() => handleToggle(f.id)}></button>
                </div>
                {f.description && <p className="wf-card-desc">{f.description}</p>}
                {steps.length > 0 && (
                  <div style={{ padding: '0 0 6px', fontSize: 11, color: 'var(--color-text-secondary)' }}>
                    {steps.length} 个步骤：{steps.slice(0, 2).map((s, i) => (
                      <span key={i} style={{ display: 'block', padding: '2px 0', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {i + 1}. {s}
                      </span>
                    ))}
                    {steps.length > 2 && <span style={{ color: 'var(--color-ink-400)' }}>...还有 {steps.length - 2} 步</span>}
                  </div>
                )}
                <div className="wf-card-meta">
                  <span className={`feat-badge ${trig.cls}`}>{trig.ico} {trig.label}</span>
                  {f.lastRunAt && <span>上次: {new Date(f.lastRunAt).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })}</span>}
                </div>
                <div className="wf-card-actions">
                  <button className="feat-btn small primary" onClick={() => handleRun(f.id)} disabled={isRunning}>
                    {isRunning ? '⏳ 执行中...' : '▶ 运行'}
                  </button>
                  <button className="feat-btn small ghost" onClick={() => loadRuns(f.id)}>历史</button>
                  <button className="feat-btn small subtle" onClick={() => handleDelete(f.id)}>删除</button>
                </div>
              </div>
            );
          })}

          {flows.length === 0 && (
            <div style={{ gridColumn: '1/3', textAlign: 'center', padding: 40, color: 'var(--color-ink-400)' }}>
              <div style={{ fontSize: 32, marginBottom: 12 }}>⚙️</div>
              <div style={{ fontSize: 14, marginBottom: 4 }}>创建你的第一个 AI 工作流</div>
              <div style={{ fontSize: 12, marginBottom: 12, color: 'var(--color-text-secondary)' }}>
                工作流可以将多个 AI 指令串联执行，例如"总结笔记 → 提取待办 → 生成日报"
              </div>
              <button className="feat-btn primary" onClick={() => setShowCreate(true)}>+ 创建工作流</button>
            </div>
          )}
        </div>
      )}

      {tab === 'history' && (
        <div>
          <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', marginBottom: 12, paddingBottom: 10, borderBottom: '1px solid var(--color-border)' }}>
            <span style={{ fontWeight: 600 }}>{flows.find(f => f.id === selectedWf)?.name || '运行历史'}</span>
            <span style={{ fontSize: 11, color: 'var(--color-ink-400)' }}>{runs.length} 次运行</span>
          </div>
          {runs.map(r => (
            <div key={r.id} className="wf-hist-row" onClick={() => setShowResult(r)} style={{ cursor: 'pointer' }}>
              <span className={`s-dot ${r.status === 'completed' ? 'good' : r.status === 'running' ? 'run' : r.status === 'failed' ? 'fail' : 'queue'}`}></span>
              <span style={{ color: 'var(--color-text-secondary)', fontSize: 12 }}>{r.startedAt ? new Date(r.startedAt).toLocaleString('zh-CN') : '—'}</span>
              <span className={`feat-badge ${r.status === 'completed' ? 'good' : r.status === 'failed' ? 'danger' : 'info'}`}>{r.status}</span>
              <span style={{ fontSize: 12 }}>{r.completedAt ? `耗时 ${Math.round((new Date(r.completedAt).getTime() - new Date(r.startedAt).getTime()) / 1000)}s` : '—'}</span>
              <span style={{ fontSize: 11, color: 'var(--color-accent)' }}>查看详情 →</span>
            </div>
          ))}
          {runs.length === 0 && <div style={{ padding: 20, textAlign: 'center', color: 'var(--color-ink-400)', fontSize: 13 }}>暂无运行记录</div>}
        </div>
      )}

      {/* Create modal */}
      {showCreate && (
        <div className="lp-modal-bg" onClick={() => setShowCreate(false)}>
          <div className="lp-modal" onClick={e => e.stopPropagation()} style={{ maxWidth: 520 }}>
            <div className="lp-modal-head">
              <span style={{ fontWeight: 600 }}>创建工作流</span>
              <button className="feat-btn subtle small" onClick={() => setShowCreate(false)}>×</button>
            </div>
            <div className="lp-modal-body">
              <div>
                <label style={{ fontSize: 11, color: 'var(--color-text-secondary)', display: 'block', marginBottom: 5 }}>名称</label>
                <input className="feat-input" placeholder="工作流名称" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} />
              </div>
              <div>
                <label style={{ fontSize: 11, color: 'var(--color-text-secondary)', display: 'block', marginBottom: 5 }}>触发方式</label>
                <div style={{ display: 'flex', gap: 8 }}>
                  {['manual', 'schedule', 'event'].map(t => (
                    <button key={t} className={`feat-btn small ${form.triggerType === t ? 'primary' : 'ghost'}`} onClick={() => setForm({ ...form, triggerType: t })}>
                      {t === 'manual' ? '✋ 手动' : t === 'schedule' ? '◷ 定时' : '⚡ 事件'}
                    </button>
                  ))}
                </div>
              </div>
              <div>
                <label style={{ fontSize: 11, color: 'var(--color-text-secondary)', display: 'block', marginBottom: 5 }}>
                  执行步骤（每行一个 AI 指令，按顺序执行）
                </label>
                <textarea
                  className="feat-input"
                  rows={5}
                  placeholder={"总结我所有笔记的核心观点\n从笔记中提取所有待办事项\n生成一份今日工作日报"}
                  value={form.steps}
                  onChange={e => setForm({ ...form, steps: e.target.value })}
                  style={{ fontFamily: 'inherit', lineHeight: 1.6 }}
                />
                <div style={{ fontSize: 10, color: 'var(--color-ink-400)', marginTop: 4, lineHeight: 1.6 }}>
                  每行是一个 AI 步骤，按顺序执行，后续步骤可引用前一步结果。
                </div>
                <div style={{ fontSize: 10, color: 'var(--color-ink-400)', marginTop: 6, background: 'var(--color-paper-2, #f5efe0)', padding: '8px 10px', borderRadius: 6, lineHeight: 1.8 }}>
                  <div style={{ fontWeight: 600, marginBottom: 2 }}>示例：</div>
                  <div>日报：总结今天的笔记 → 提取待办 → 生成日报</div>
                  <div>整理：搜索"机器学习"笔记 → 归纳要点 → 生成学习路线</div>
                </div>
              </div>
            </div>
            <div className="lp-modal-foot">
              <button className="feat-btn ghost" onClick={() => setShowCreate(false)}>取消</button>
              <button className="feat-btn primary" onClick={handleCreate} disabled={!form.name || !form.steps.trim()}>创建</button>
            </div>
          </div>
        </div>
      )}

      {/* Run result modal */}
      {showResult && (
        <div className="lp-modal-bg" onClick={() => setShowResult(null)}>
          <div className="lp-modal" onClick={e => e.stopPropagation()} style={{ maxWidth: 600, maxHeight: '80vh' }}>
            <div className="lp-modal-head">
              <span style={{ fontWeight: 600 }}>运行详情</span>
              <button className="feat-btn subtle small" onClick={() => setShowResult(null)}>×</button>
            </div>
            <div style={{ padding: '12px 16px', overflowY: 'auto', maxHeight: '60vh' }}>
              <div style={{ display: 'flex', gap: 12, marginBottom: 12, fontSize: 12 }}>
                <span className={`feat-badge ${showResult.status === 'completed' ? 'good' : showResult.status === 'failed' ? 'danger' : 'info'}`}>{showResult.status}</span>
                <span style={{ color: 'var(--color-text-secondary)' }}>{new Date(showResult.startedAt).toLocaleString('zh-CN')}</span>
                {showResult.completedAt && <span style={{ color: 'var(--color-text-secondary)' }}>耗时 {Math.round((new Date(showResult.completedAt).getTime() - new Date(showResult.startedAt).getTime()) / 1000)}s</span>}
              </div>

              {showResult.error && (
                <div style={{ padding: '10px 12px', background: 'rgba(184,69,46,0.08)', borderRadius: 6, fontSize: 12, color: 'var(--color-accent)', marginBottom: 12 }}>
                  错误: {showResult.error}
                </div>
              )}

              {parseResults(showResult.results).map((step: any, i: number) => (
                <div key={i} style={{ marginBottom: 14, padding: '12px 14px', background: 'var(--color-paper-2, #f5efe0)', borderRadius: 8, border: '1px solid var(--color-border)' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
                    <span style={{ fontSize: 11, fontWeight: 600, color: 'var(--color-accent)' }}>步骤 {step.step}</span>
                    <span className={`feat-badge ${step.status === 'completed' ? 'good' : 'danger'}`} style={{ fontSize: 10 }}>{step.status}</span>
                  </div>
                  <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 6, color: 'var(--color-text)' }}>{step.instruction}</div>
                  {step.output && (
                    <div style={{ fontSize: 12, color: 'var(--color-text-secondary)', lineHeight: 1.6, whiteSpace: 'pre-wrap', maxHeight: 200, overflowY: 'auto', background: 'var(--color-paper-0)', padding: '8px 10px', borderRadius: 6 }}>
                      {step.output}
                    </div>
                  )}
                  {step.error && (
                    <div style={{ fontSize: 11, color: 'var(--color-accent)', marginTop: 4 }}>
                      失败: {step.error}
                    </div>
                  )}
                </div>
              ))}

              {!showResult.results && showResult.status === 'queued' && (
                <div style={{ textAlign: 'center', padding: 20, color: 'var(--color-ink-400)', fontSize: 13 }}>等待执行中...</div>
              )}
            </div>
            <div className="lp-modal-foot">
              <button className="feat-btn ghost" onClick={() => setShowResult(null)}>关闭</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
