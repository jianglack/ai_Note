import { useState, useEffect, useRef } from 'react';
import { getEvalDatasets, getEvalDataset, createEvalDataset, addEvalItem, startEvalRun, getEvalRuns, getEvalRunDetail, deleteEvalDataset, EvalResultData } from '../../api';
import { askConfirm } from '../../services/dialogService';
import './features.css';

interface Dataset { id: string; name: string; description?: string; datasetType: string; createdAt: string; }
interface EvalItem { id: string; question: string; expectedAnswer: string; }
interface EvalRun { id: string; datasetId: string; runType: string; status: string; summary?: string; startedAt: string; completedAt?: string; }

export default function EvalDashView({ onClose }: { onClose: () => void }) {
  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [activeDs, setActiveDs] = useState<string | null>(null);
  const [items, setItems] = useState<EvalItem[]>([]);
  const [runs, setRuns] = useState<EvalRun[]>([]);
  const [tab, setTab] = useState<'items' | 'history'>('items');
  const [showAdd, setShowAdd] = useState(false);
  const [showAddItem, setShowAddItem] = useState(false);
  const [form, setForm] = useState({ name: '', description: '', type: 'rag' });
  const [itemForm, setItemForm] = useState({ question: '', expectedAnswer: '' });
  const [running, setRunning] = useState(false);
  // 运行详情
  const [selectedRun, setSelectedRun] = useState<EvalRun | null>(null);
  const [runResults, setRunResults] = useState<EvalResultData[]>([]);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => { loadDatasets(); }, []);
  useEffect(() => () => { clearPolling(); }, []);

  const clearPolling = () => {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  };

  const loadDatasets = async () => {
    try { setDatasets(await getEvalDatasets()); } catch (e) { console.error(e); }
  };

  const selectDataset = async (id: string) => {
    setActiveDs(id);
    setSelectedRun(null);
    setRunResults([]);
    try {
      const detail = await getEvalDataset(id);
      setItems((detail.items || []).map((item: any) => ({
        id: item.id, question: item.question, expectedAnswer: item.expectedAnswer || '',
      })));
      const allRuns = await getEvalRuns();
      setRuns(allRuns.filter((run: any) => run.datasetId === id));
    } catch (e) { console.error(e); }
  };

  const handleCreateDs = async () => {
    try {
      await createEvalDataset(form.name, form.description, form.type);
      setShowAdd(false); setForm({ name: '', description: '', type: 'rag' }); loadDatasets();
    } catch (e) { console.error(e); }
  };

  const handleAddItem = async () => {
    if (!activeDs) return;
    try {
      await addEvalItem(activeDs, itemForm.question, itemForm.expectedAnswer);
      setShowAddItem(false); setItemForm({ question: '', expectedAnswer: '' }); selectDataset(activeDs);
    } catch (e) { console.error(e); }
  };

  const handleStartRun = async () => {
    if (!activeDs) return;
    const ds = datasets.find(d => d.id === activeDs);
    const runType = ds?.datasetType === 'agent' ? 'agent' : 'rag';
    clearPolling();
    setRunning(true);
    try {
      await startEvalRun(activeDs, runType);
      setTab('history');
      pollRef.current = setInterval(async () => {
        try {
          const allRuns = await getEvalRuns();
          const dsRuns = allRuns.filter((r: any) => r.datasetId === activeDs);
          setRuns(dsRuns);
          const latest = dsRuns[0];
          if (!latest || latest.status === 'completed' || latest.status === 'failed') {
            clearPolling(); setRunning(false);
            if (latest) viewRunDetail(latest);
          }
        } catch { clearPolling(); setRunning(false); }
      }, 3000);
    } catch (e) { console.error(e); setRunning(false); }
  };

  const viewRunDetail = async (run: EvalRun) => {
    setSelectedRun(run);
    try {
      const detail = await getEvalRunDetail(run.id);
      setRunResults(detail.results || []);
    } catch (e) { console.error(e); }
  };

  const handleDeleteDs = async (id: string) => {
    if (!await askConfirm({
      title: 'Delete dataset',
      message: 'Delete this evaluation dataset?',
      confirmLabel: 'Delete',
      danger: true,
    })) return;
    try { await deleteEvalDataset(id); setActiveDs(null); loadDatasets(); } catch (e) { console.error(e); }
  };

  // 解析 summary JSON
  const parseSummary = (run: EvalRun): Record<string, number> => {
    if (!run.summary) return {};
    try { return JSON.parse(run.summary); } catch { return {}; }
  };

  const fmtScore = (v: number | null | undefined) => v == null ? '—' : (v * 100).toFixed(1) + '%';
  const scoreColor = (v: number | null | undefined) => v == null ? 'var(--color-ink-400)' : v >= 0.8 ? '#4a8c5c' : v >= 0.5 ? '#c9a959' : '#b8452e';

  const metricLabels: Record<string, string> = {
    // RAG (RAGAS)
    faithfulness: '忠实度',
    answerRelevancy: '答案相关性',
    contextPrecision: '上下文精度',
    contextRecall: '上下文召回',
    factualCorrectness: '事实正确性',
    // Agent (RAGAS)
    agentGoalAccuracy: '目标达成率',
    toolCallAccuracy: '工具调用准确率',
    topicAdherence: '主题一致性',
    errorRecoveryRate: '错误恢复率',
  };

  const fmtTokens = (v: number | null | undefined) => v == null ? '—' : v > 1000 ? (v / 1000).toFixed(1) + 'k' : v.toString();

  return (
    <div className="ev-root">
      <div className="ev-top">
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <button className="feat-btn subtle small" onClick={onClose}>←</button>
          <h1 style={{ fontSize: 20, fontWeight: 600, margin: 0 }}>评估中心</h1>
          <span className="feat-badge">{datasets.length} 数据集</span>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="feat-btn ghost" onClick={() => setShowAdd(true)}>+ 新建数据集</button>
          <button className="feat-btn primary" onClick={handleStartRun} disabled={!activeDs || running || items.length === 0}>
            {running ? '⏳ 评估中...' : '▶ 开始评估'}
          </button>
        </div>
      </div>

      {/* 统计卡片 */}
      <div className="ev-stats">
        {[
          { label: '数据集', value: datasets.length.toString() },
          { label: '评估项', value: items.length.toString() },
          { label: '运行次数', value: runs.length.toString() },
          { label: '最近状态', value: runs[0]?.status === 'completed' ? '✓ 完成' : runs[0]?.status === 'failed' ? '✗ 失败' : runs[0]?.status || '—' },
        ].map(s => (
          <div key={s.label} className="ev-stat">
            <div className="ev-stat-label">{s.label}</div>
            <div style={{ marginTop: 6 }}><span className="ev-stat-val" style={{ fontSize: 22 }}>{s.value}</span></div>
          </div>
        ))}
      </div>

      <div className="ev-main">
        {/* 左侧数据集列表 */}
        <div className="ev-side">
          <div className="ev-side-h">
            <span style={{ fontWeight: 600 }}>数据集</span>
            <span className="feat-badge">{datasets.length}</span>
          </div>
          <div style={{ padding: 6, flex: 1, overflowY: 'auto' }}>
            {datasets.map(d => (
              <div key={d.id} className={`ev-ds ${activeDs === d.id ? 'on' : ''}`} onClick={() => selectDataset(d.id)}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
                  <span className="ev-ds-name">{d.name}</span>
                  <span className={`feat-badge ${d.datasetType === 'rag' ? 'info' : 'accent'}`}>{d.datasetType}</span>
                </div>
                <div className="ev-ds-meta">
                  <span>{d.description || '无描述'}</span>
                  <button className="feat-btn subtle small" onClick={e => { e.stopPropagation(); handleDeleteDs(d.id); }}>⌫</button>
                </div>
              </div>
            ))}
            <button className="lp-add-cta" onClick={() => setShowAdd(true)}>+ 创建新数据集</button>
          </div>
        </div>

        {/* 右侧内容区 */}
        <div className="ev-content">
          <div className="ev-content-h">
            <div className="ev-tabs">
              <button className={tab === 'items' ? 'on' : ''} onClick={() => { setTab('items'); setSelectedRun(null); }}>评估项</button>
              <button className={tab === 'history' ? 'on' : ''} onClick={() => setTab('history')}>运行历史</button>
            </div>
            {activeDs && tab === 'items' && <button className="feat-btn small ghost" onClick={() => setShowAddItem(true)}>+ 添加项</button>}
          </div>

          {/* 评估项标签页 */}
          {tab === 'items' && (
            <div style={{ padding: 8, flex: 1, overflowY: 'auto' }}>
              {items.length === 0 && <div style={{ padding: 20, textAlign: 'center', color: 'var(--color-ink-400)', fontSize: 13 }}>{activeDs ? '暂无评估项，点击「+ 添加项」创建' : '选择左侧数据集查看评估项'}</div>}
              {items.map((item, idx) => (
                <div key={item.id} style={{ padding: '10px 12px', borderBottom: '1px dashed var(--color-border)', fontSize: 13 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <span style={{ fontSize: 11, color: 'var(--color-ink-400)', fontWeight: 600 }}>Q{idx + 1}</span>
                    <span style={{ fontWeight: 500 }}>{item.question}</span>
                  </div>
                  {item.expectedAnswer && <div style={{ fontSize: 12, color: 'var(--color-text-secondary)', marginTop: 4, paddingLeft: 24 }}>期望: {item.expectedAnswer}</div>}
                </div>
              ))}
            </div>
          )}

          {/* 运行历史标签页 */}
          {tab === 'history' && !selectedRun && (
            <div style={{ padding: 8, flex: 1, overflowY: 'auto' }}>
              {runs.length === 0 && <div style={{ padding: 20, textAlign: 'center', color: 'var(--color-ink-400)', fontSize: 13 }}>{running ? '评估运行中，请稍候...' : '暂无运行记录，点击「开始评估」'}</div>}
              {runs.map(r => {
                const summary = parseSummary(r);
                const metrics = Object.entries(summary).filter(([k]) => k in metricLabels);
                return (
                  <div key={r.id} onClick={() => viewRunDetail(r)}
                    style={{ padding: '12px 14px', marginBottom: 8, background: 'var(--color-paper-2, #f5efe0)', borderRadius: 8, border: '1px solid var(--color-border)', cursor: 'pointer' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
                      <span className={`s-dot ${r.status === 'completed' ? 'good' : r.status === 'running' ? 'run' : 'fail'}`}></span>
                      <span style={{ fontSize: 12.5, fontWeight: 500 }}>{r.startedAt ? new Date(r.startedAt).toLocaleString('zh-CN') : '—'}</span>
                      <span className={`feat-badge ${r.status === 'completed' ? 'good' : r.status === 'failed' ? 'danger' : 'info'}`}>{r.status === 'completed' ? '完成' : r.status === 'failed' ? '失败' : r.status}</span>
                      <span style={{ fontSize: 11, color: 'var(--color-ink-400)' }}>{r.runType}</span>
                      {r.completedAt && r.startedAt && <span style={{ fontSize: 11, color: 'var(--color-ink-400)', marginLeft: 'auto' }}>耗时 {Math.round((new Date(r.completedAt).getTime() - new Date(r.startedAt).getTime()) / 1000)}s</span>}
                    </div>
                    {/* 指标概览 */}
                    {metrics.length > 0 && (
                      <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
                        {metrics.map(([k, v]) => (
                          <div key={k} style={{ textAlign: 'center', minWidth: 70 }}>
                            <div style={{ fontSize: 18, fontWeight: 700, color: scoreColor(v as number) }}>{fmtScore(v as number)}</div>
                            <div style={{ fontSize: 10, color: 'var(--color-ink-400)', marginTop: 2 }}>{metricLabels[k] || k}</div>
                          </div>
                        ))}
                        {summary.avgTokens != null && (
                          <div style={{ textAlign: 'center', minWidth: 70 }}>
                            <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--color-text)' }}>{fmtTokens(summary.avgTokens as number)}</div>
                            <div style={{ fontSize: 10, color: 'var(--color-ink-400)', marginTop: 2 }}>平均Token</div>
                          </div>
                        )}
                        {summary.totalItems != null && (
                          <div style={{ textAlign: 'center', minWidth: 70 }}>
                            <div style={{ fontSize: 18, fontWeight: 700 }}>{summary.evaluatedItems}/{summary.totalItems}</div>
                            <div style={{ fontSize: 10, color: 'var(--color-ink-400)', marginTop: 2 }}>评估/总计</div>
                          </div>
                        )}
                      </div>
                    )}
                    {metrics.length === 0 && r.status === 'completed' && (
                      <div style={{ fontSize: 11, color: 'var(--color-ink-400)' }}>点击查看详细结果</div>
                    )}
                  </div>
                );
              })}
            </div>
          )}

          {/* 运行详情视图 */}
          {tab === 'history' && selectedRun && (
            <div style={{ padding: 8, flex: 1, overflowY: 'auto' }}>
              <button className="feat-btn small ghost" onClick={() => { setSelectedRun(null); setRunResults([]); }} style={{ marginBottom: 10 }}>← 返回列表</button>

              {/* 总体指标卡片 */}
              {(() => {
                const summary = parseSummary(selectedRun);
                const metrics = Object.entries(summary).filter(([k]) => k in metricLabels);
                return metrics.length > 0 ? (
                  <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginBottom: 16, padding: '14px 16px', background: 'var(--color-paper-2, #f5efe0)', borderRadius: 10, border: '1px solid var(--color-border)' }}>
                    {metrics.map(([k, v]) => (
                      <div key={k} style={{ flex: 1, minWidth: 90, textAlign: 'center' }}>
                        <div style={{ fontSize: 24, fontWeight: 700, color: scoreColor(v as number) }}>{fmtScore(v as number)}</div>
                        <div style={{ fontSize: 11, color: 'var(--color-ink-400)', marginTop: 3 }}>{metricLabels[k] || k}</div>
                        {/* 进度条 */}
                        <div style={{ marginTop: 6, height: 4, background: 'var(--color-border)', borderRadius: 2, overflow: 'hidden' }}>
                          <div style={{ height: '100%', width: `${((v as number) || 0) * 100}%`, background: scoreColor(v as number), borderRadius: 2 }} />
                        </div>
                      </div>
                    ))}
                  </div>
                ) : null;
              })()}

              {/* 每项详细结果 */}
              <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>逐项结果 ({runResults.length} 项)</div>
              {runResults.map((r, idx) => {
                const item = items.find(i => i.id === r.itemId);
                return (
                  <div key={r.id || idx} style={{ marginBottom: 10, padding: '12px 14px', background: 'var(--color-paper-0, #fbf7ee)', borderRadius: 8, border: '1px solid var(--color-border)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                      <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--color-accent)', background: 'rgba(184,69,46,0.08)', padding: '2px 6px', borderRadius: 4 }}>Q{idx + 1}</span>
                      <span style={{ fontSize: 13, fontWeight: 500, flex: 1 }}>{item?.question || '—'}</span>
                      {r.latencyMs != null && <span style={{ fontSize: 10, color: 'var(--color-ink-400)' }}>{r.latencyMs}ms</span>}
                    </div>

                    {/* 指标行 */}
                    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 8 }}>
                      {r.faithfulness != null && <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 4, background: `${scoreColor(r.faithfulness)}18`, color: scoreColor(r.faithfulness), fontWeight: 600 }}>忠实度 {fmtScore(r.faithfulness)}</span>}
                      {r.answerRelevancy != null && <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 4, background: `${scoreColor(r.answerRelevancy)}18`, color: scoreColor(r.answerRelevancy), fontWeight: 600 }}>相关性 {fmtScore(r.answerRelevancy)}</span>}
                      {r.contextPrecision != null && <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 4, background: `${scoreColor(r.contextPrecision)}18`, color: scoreColor(r.contextPrecision), fontWeight: 600 }}>精度 {fmtScore(r.contextPrecision)}</span>}
                      {r.contextRecall != null && <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 4, background: `${scoreColor(r.contextRecall)}18`, color: scoreColor(r.contextRecall), fontWeight: 600 }}>召回 {fmtScore(r.contextRecall)}</span>}
                      {r.taskCompletion != null && <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 4, background: `${scoreColor(r.taskCompletion)}18`, color: scoreColor(r.taskCompletion), fontWeight: 600 }}>目标达成 {fmtScore(r.taskCompletion)}</span>}
                      {r.toolAccuracy != null && <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 4, background: `${scoreColor(r.toolAccuracy)}18`, color: scoreColor(r.toolAccuracy), fontWeight: 600 }}>工具准确率 {fmtScore(r.toolAccuracy)}</span>}
                      {r.consistency != null && <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 4, background: `${scoreColor(r.consistency)}18`, color: scoreColor(r.consistency), fontWeight: 600 }}>主题一致性 {fmtScore(r.consistency)}</span>}
                      {r.errorRecoveryRate != null && <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 4, background: `${scoreColor(r.errorRecoveryRate)}18`, color: scoreColor(r.errorRecoveryRate), fontWeight: 600 }}>错误恢复率 {fmtScore(r.errorRecoveryRate)}</span>}
                      {r.totalTokens != null && r.totalTokens > 0 && <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 4, background: 'rgba(0,0,0,0.05)', color: 'var(--color-ink-500)', fontWeight: 600 }}>{fmtTokens(r.totalTokens)} tokens</span>}
                    </div>

                    {/* 工具调用详情 */}
                    {r.actualToolCalls && (() => {
                      try {
                        const tools = JSON.parse(r.actualToolCalls);
                        if (Array.isArray(tools) && tools.length > 0) {
                          return (
                            <div style={{ marginBottom: 8 }}>
                              <div style={{ fontSize: 10, color: 'var(--color-ink-400)', fontWeight: 600, marginBottom: 3 }}>实际工具调用</div>
                              <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                                {tools.map((t: any, i: number) => (
                                  <span key={i} style={{ fontSize: 10, padding: '1px 6px', borderRadius: 3, background: 'rgba(74,140,92,0.1)', color: '#4a8c5c', fontFamily: 'monospace' }}>
                                    {typeof t === 'string' ? t : t.name || t.tool || JSON.stringify(t)}
                                  </span>
                                ))}
                              </div>
                            </div>
                          );
                        }
                      } catch { /* ignore */ }
                      return null;
                    })()}

                    {/* 期望 vs 实际对比 */}
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, fontSize: 12 }}>
                      <div>
                        <div style={{ fontSize: 10, color: 'var(--color-ink-400)', fontWeight: 600, marginBottom: 3 }}>期望答案</div>
                        <div style={{ padding: '6px 8px', background: 'var(--color-paper-2, #f0eadb)', borderRadius: 5, lineHeight: 1.5, maxHeight: 100, overflowY: 'auto', color: 'var(--color-text-secondary)' }}>
                          {item?.expectedAnswer || '—'}
                        </div>
                      </div>
                      <div>
                        <div style={{ fontSize: 10, color: 'var(--color-ink-400)', fontWeight: 600, marginBottom: 3 }}>实际回答</div>
                        <div style={{ padding: '6px 8px', background: 'var(--color-paper-2, #f0eadb)', borderRadius: 5, lineHeight: 1.5, maxHeight: 100, overflowY: 'auto' }}>
                          {r.actualAnswer || '—'}
                        </div>
                      </div>
                    </div>
                  </div>
                );
              })}
              {runResults.length === 0 && <div style={{ padding: 20, textAlign: 'center', color: 'var(--color-ink-400)', fontSize: 13 }}>无详细结果数据</div>}
            </div>
          )}
        </div>
      </div>

      {/* Create dataset modal */}
      {showAdd && (
        <div className="lp-modal-bg" onClick={() => setShowAdd(false)}>
          <div className="lp-modal" onClick={e => e.stopPropagation()}>
            <div className="lp-modal-head">
              <span style={{ fontWeight: 600 }}>新建数据集</span>
              <button className="feat-btn subtle small" onClick={() => setShowAdd(false)}>×</button>
            </div>
            <div className="lp-modal-body">
              <div>
                <label style={{ fontSize: 11, color: 'var(--color-text-secondary)', display: 'block', marginBottom: 5 }}>名称</label>
                <input className="feat-input" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} />
              </div>
              <div>
                <label style={{ fontSize: 11, color: 'var(--color-text-secondary)', display: 'block', marginBottom: 5 }}>描述</label>
                <input className="feat-input" value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} />
              </div>
              <div>
                <label style={{ fontSize: 11, color: 'var(--color-text-secondary)', display: 'block', marginBottom: 5 }}>类型</label>
                <div style={{ display: 'flex', gap: 8 }}>
                  {[{ k: 'rag', label: 'RAG 检索质量' }, { k: 'agent', label: 'Agent 任务能力' }].map(t => (
                    <button key={t.k} className={`feat-btn small ${form.type === t.k ? 'primary' : 'ghost'}`} onClick={() => setForm({ ...form, type: t.k })}>{t.label}</button>
                  ))}
                </div>
                <div style={{ fontSize: 10, color: 'var(--color-ink-400)', marginTop: 6, lineHeight: 1.6 }}>
                  RAG：RAGAS 标准评估（忠实度、答案相关性、上下文精度/召回、事实正确性）<br/>
                  Agent：RAGAS 标准评估（目标达成率、工具调用准确率、主题一致性、错误恢复率）
                </div>
              </div>
            </div>
            <div className="lp-modal-foot">
              <button className="feat-btn ghost" onClick={() => setShowAdd(false)}>取消</button>
              <button className="feat-btn primary" onClick={handleCreateDs} disabled={!form.name}>创建</button>
            </div>
          </div>
        </div>
      )}

      {/* Add item modal */}
      {showAddItem && (
        <div className="lp-modal-bg" onClick={() => setShowAddItem(false)}>
          <div className="lp-modal" onClick={e => e.stopPropagation()}>
            <div className="lp-modal-head">
              <span style={{ fontWeight: 600 }}>添加评估项</span>
              <button className="feat-btn subtle small" onClick={() => setShowAddItem(false)}>×</button>
            </div>
            <div className="lp-modal-body">
              <div>
                <label style={{ fontSize: 11, color: 'var(--color-text-secondary)', display: 'block', marginBottom: 5 }}>问题 *</label>
                <textarea className="feat-input" rows={2} value={itemForm.question} onChange={e => setItemForm({ ...itemForm, question: e.target.value })} placeholder="提交给模型的问题"></textarea>
              </div>
              <div>
                <label style={{ fontSize: 11, color: 'var(--color-text-secondary)', display: 'block', marginBottom: 5 }}>期望答案（选填，用于计算精度和召回）</label>
                <textarea className="feat-input" rows={3} value={itemForm.expectedAnswer} onChange={e => setItemForm({ ...itemForm, expectedAnswer: e.target.value })} placeholder="人工标注的理想回答"></textarea>
              </div>
            </div>
            <div className="lp-modal-foot">
              <button className="feat-btn ghost" onClick={() => setShowAddItem(false)}>取消</button>
              <button className="feat-btn primary" onClick={handleAddItem} disabled={!itemForm.question}>添加</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
