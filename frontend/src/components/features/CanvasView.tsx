import { useState, useEffect, useCallback, useMemo, useRef, type CSSProperties } from 'react';
import { FixedSizeList } from 'react-window';
import { getCanvases, createCanvas, updateCanvas, deleteCanvas, generateCanvasAI } from '../../api';
import { useNoteStore } from '../../stores/noteStore';
import { askConfirm, askPrompt, showAlert } from '../../services/dialogService';
import './features.css';

interface CanvasData { id: string; title: string; data: string; createdAt: string; updatedAt: string; }
interface CanvasNode { id: string; x: number; y: number; w: number; h: number; color: string; title: string; preview: string; tags: string[]; noteId?: string; group?: string; }
interface CanvasEdge { from: string; to: string; label: string; }

const COLORS = ['#b8452e', '#6b7a5a', '#c9a959', '#6b85a3', '#7a6b58', '#a06b8c'];
const NOTE_IMPORT_ROW_HEIGHT = 66;

export default function CanvasView({ onClose }: { onClose: () => void }) {
  const { notes } = useNoteStore();
  const [canvases, setCanvases] = useState<CanvasData[]>([]);
  const [currentCanvas, setCurrentCanvas] = useState<CanvasData | null>(null);
  const [nodes, setNodes] = useState<CanvasNode[]>([]);
  const [edges, setEdges] = useState<CanvasEdge[]>([]);
  const [tool, setTool] = useState<'select' | 'edge'>('select');
  const [zoom, setZoom] = useState(1);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [active, setActive] = useState<string | null>(null);
  const [showList, setShowList] = useState(true);
  const [showNoteImport, setShowNoteImport] = useState(false);
  const [edgeStart, setEdgeStart] = useState<string | null>(null);
  const [editingNode, setEditingNode] = useState<string | null>(null);
  const [aiLoading, setAiLoading] = useState(false);

  // Drag state
  const dragRef = useRef<{ nodeId: string; startX: number; startY: number; nodeStartX: number; nodeStartY: number } | null>(null);
  // Pan state
  const panRef = useRef<{ startX: number; startY: number; panStartX: number; panStartY: number } | null>(null);
  const stageRef = useRef<HTMLDivElement>(null);
  // Edge start ref (同步 state，避免闭包读旧值)
  const edgeStartRef = useRef<string | null>(null);
  edgeStartRef.current = edgeStart;
  // Editing container ref (用于判断 blur 是否离开编辑区域)
  const editContainerRef = useRef<HTMLDivElement>(null);
  const importPreviewById = useMemo(() => {
    return Object.fromEntries(notes.map((note) => [
      note.id,
      note.content.replace(/<[^>]*>/g, '').replace(/\s+/g, ' ').trim().slice(0, 40),
    ]));
  }, [notes]);
  const noteImportHeight = Math.min(360, Math.max(80, notes.length * NOTE_IMPORT_ROW_HEIGHT));

  useEffect(() => { loadCanvases(); }, []);

  const loadCanvases = async () => { try { setCanvases(await getCanvases()); } catch (e) { console.error(e); } };

  const openCanvas = (canvas: CanvasData) => {
    setCurrentCanvas(canvas); setShowList(false);
    try { const p = JSON.parse(canvas.data || '{"nodes":[],"edges":[]}'); setNodes(p.nodes || []); setEdges(p.edges || []); }
    catch { setNodes([]); setEdges([]); }
    setPan({ x: 0, y: 0 }); setZoom(1);
  };

  const handleCreate = async () => {
    const title = await askPrompt({
      title: '新建画布',
      message: '画布名称',
      defaultValue: '新画布',
      confirmLabel: '创建',
    });
    if (!title) return;
    try { const c = await createCanvas(title, '{"nodes":[],"edges":[]}'); loadCanvases(); openCanvas(c); } catch (e) { console.error(e); }
  };

  const handleSave = async () => {
    if (!currentCanvas) return;
    try {
      const dataStr = JSON.stringify({ nodes, edges });
      await updateCanvas(currentCanvas.id, currentCanvas.title, dataStr);
      // 同步更新本地状态，避免回到列表再打开时数据丢失
      setCurrentCanvas({ ...currentCanvas, data: dataStr });
      setCanvases(prev => prev.map(c => c.id === currentCanvas.id ? { ...c, data: dataStr, title: currentCanvas.title } : c));
    } catch (e) { console.error(e); }
  };

  const addNode = () => {
    const cx = (-pan.x + 400) / zoom;
    const cy = (-pan.y + 300) / zoom;
    const newNode: CanvasNode = {
      id: 'n' + Date.now(), x: cx + Math.random() * 200, y: cy + Math.random() * 200,
      w: 220, h: 130, color: COLORS[Math.floor(Math.random() * COLORS.length)],
      title: '新卡片', preview: '双击编辑…', tags: [],
    };
    setNodes(prev => [...prev, newNode]); setActive(newNode.id);
  };

  const importNote = (noteId: string) => {
    const note = notes.find(n => n.id === noteId);
    if (!note || nodes.some(n => n.noteId === noteId)) return;
    const cx = (-pan.x + 400) / zoom; const cy = (-pan.y + 300) / zoom;
    const newNode: CanvasNode = {
      id: 'n' + Date.now(), x: cx + Math.random() * 300, y: cy + Math.random() * 200,
      w: 220, h: 130, color: COLORS[Math.floor(Math.random() * COLORS.length)],
      title: note.title || '无标题', preview: note.content.replace(/<[^>]*>/g, '').slice(0, 100),
      tags: note.tags?.map(t => t.name) || [], noteId: note.id,
    };
    setNodes(prev => [...prev, newNode]); setActive(newNode.id);
  };

  const deleteNode = (id: string) => {
    setNodes(prev => prev.filter(n => n.id !== id));
    setEdges(prev => prev.filter(e => e.from !== id && e.to !== id));
    if (active === id) setActive(null);
  };

  // AI 生成画布
  const handleAiGenerate = async () => {
    if (notes.length === 0) {
      await showAlert({
        title: '无法生成画布',
        message: '请先创建一些笔记',
      });
      return;
    }
    setAiLoading(true);
    try {
      const result = await generateCanvasAI();
      const aiNodes: CanvasNode[] = (result.nodes || []).map((n: any, i: number) => ({
        id: n.noteId || ('ai' + i + Date.now()), x: n.x || 100 + i * 250, y: n.y || 100,
        w: 220, h: 130, color: n.color || COLORS[i % COLORS.length],
        title: n.title || '未知', preview: n.preview || '',
        tags: [], noteId: n.noteId || undefined, group: n.group,
      }));
      const aiEdges: CanvasEdge[] = (result.edges || []).map((e: any) => ({
        from: e.from, to: e.to, label: e.label || '',
      }));
      setNodes(aiNodes); setEdges(aiEdges);
      // 居中视图
      if (aiNodes.length > 0) {
        const avgX = aiNodes.reduce((s, n) => s + n.x, 0) / aiNodes.length;
        const avgY = aiNodes.reduce((s, n) => s + n.y, 0) / aiNodes.length;
        setPan({ x: -avgX + 400, y: -avgY + 300 });
      }
    } catch (e) {
      console.error(e);
      await showAlert({
        title: 'AI 生成失败',
        message: '请重试',
      });
    }
    setAiLoading(false);
  };

  // --- Node drag ---
  const handleNodeMouseDown = (e: React.MouseEvent, nodeId: string) => {
    e.stopPropagation();
    if (tool === 'edge') return;
    e.preventDefault();
    const node = nodes.find(n => n.id === nodeId);
    if (!node) return;
    dragRef.current = { nodeId, startX: e.clientX, startY: e.clientY, nodeStartX: node.x, nodeStartY: node.y };
    setActive(nodeId);
    const onMove = (ev: MouseEvent) => {
      const drag = dragRef.current;
      if (!drag) return;
      const dx = (ev.clientX - drag.startX) / zoom;
      const dy = (ev.clientY - drag.startY) / zoom;
      const nx = drag.nodeStartX + dx; const ny = drag.nodeStartY + dy;
      const tid = drag.nodeId;
      setNodes(prev => prev.map(n => n.id === tid ? { ...n, x: nx, y: ny } : n));
    };
    const onUp = () => { dragRef.current = null; window.removeEventListener('mousemove', onMove); window.removeEventListener('mouseup', onUp); };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  };

  // --- Canvas pan ---
  const handleStagePan = (e: React.MouseEvent) => {
    if (dragRef.current) return;
    if (tool === 'edge') return; // don't pan in edge mode
    // Only pan if clicking on stage background (not on a node)
    if (e.target !== e.currentTarget && !(e.target as HTMLElement).classList?.contains('cv-grid')) return;
    e.preventDefault();
    panRef.current = { startX: e.clientX, startY: e.clientY, panStartX: pan.x, panStartY: pan.y };
    const onMove = (ev: MouseEvent) => {
      const p = panRef.current;
      if (!p) return;
      setPan({ x: p.panStartX + (ev.clientX - p.startX), y: p.panStartY + (ev.clientY - p.startY) });
    };
    const onUp = () => { panRef.current = null; window.removeEventListener('mousemove', onMove); window.removeEventListener('mouseup', onUp); };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  };

  // --- Zoom ---
  const handleWheel = useCallback((e: WheelEvent) => {
    e.preventDefault();
    const delta = e.deltaY > 0 ? 0.9 : 1.1;
    setZoom(prev => Math.min(3, Math.max(0.2, prev * delta)));
  }, []);

  useEffect(() => {
    const el = stageRef.current;
    if (!el || showList) return;
    el.addEventListener('wheel', handleWheel, { passive: false });
    return () => el.removeEventListener('wheel', handleWheel);
  }, [handleWheel, showList]);

  // --- Edge creation (用 ref 读最新值，避免闭包问题) ---
  const handleNodeClick = (nodeId: string) => {
    setActive(nodeId);
    if (tool !== 'edge') return;
    const currentEdgeStart = edgeStartRef.current;
    if (!currentEdgeStart) {
      setEdgeStart(nodeId);
    } else if (currentEdgeStart === nodeId) {
      setEdgeStart(null);
    } else {
      if (!edges.some(e => e.from === currentEdgeStart && e.to === nodeId)) {
        setEdges(prev => [...prev, { from: currentEdgeStart, to: nodeId, label: '' }]);
      }
      setEdgeStart(null);
    }
  };

  const handleDeleteCanvas = async (id: string) => {
    const confirmed = await askConfirm({
      title: '删除画布',
      message: '确定删除？',
      confirmLabel: '删除',
      danger: true,
    });
    if (!confirmed) return;
    try { await deleteCanvas(id); loadCanvases(); if (currentCanvas?.id === id) { setCurrentCanvas(null); setShowList(true); } } catch (e) { console.error(e); }
  };

  const renderNoteImportRow = ({ index, style }: { index: number; style: CSSProperties }) => {
    const note = notes[index];
    const imported = nodes.some(n => n.noteId === note.id);

    return (
      <div
        key={note.id}
        style={{
          ...style,
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          padding: '8px 10px',
          borderBottom: '1px solid var(--color-border)',
          cursor: imported ? 'default' : 'pointer',
          opacity: imported ? 0.5 : 1,
          boxSizing: 'border-box',
        }}
        onClick={() => { if (!imported) importNote(note.id); }}
      >
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 13, fontWeight: 500 }}>{note.title || 'Untitled'}</div>
          <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', marginTop: 2 }}>{importPreviewById[note.id]}</div>
        </div>
        {imported ? <span style={{ fontSize: 10, color: 'var(--color-ink-400)' }}>Imported</span> : <button className="feat-btn small primary">Import</button>}
      </div>
    );
  };

  // --- Canvas list view ---
  if (showList) {
    return (
      <div className="wf-root">
        <div className="wf-head">
          <div>
            <h1 className="wf-title">画布</h1>
            <p style={{ margin: '4px 0 0', fontSize: 13, color: 'var(--color-text-secondary)' }}>空间化组织笔记关系，支持 AI 自动生成</p>
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <button className="feat-btn ghost" onClick={onClose}>← 返回</button>
            <button className="feat-btn primary" onClick={handleCreate}>+ 新建画布</button>
          </div>
        </div>
        <div className="wf-grid">
          {canvases.map(c => (
            <div key={c.id} className="wf-card" onClick={() => openCanvas(c)}>
              <div className="wf-card-top"><span className="wf-card-name">{c.title}</span></div>
              <div className="wf-card-meta"><span>更新于 {new Date(c.updatedAt).toLocaleDateString('zh-CN')}</span></div>
              <div className="wf-card-actions">
                <button className="feat-btn small primary" onClick={e => { e.stopPropagation(); openCanvas(c); }}>打开</button>
                <button className="feat-btn small subtle" onClick={e => { e.stopPropagation(); handleDeleteCanvas(c.id); }}>删除</button>
              </div>
            </div>
          ))}
          {canvases.length === 0 && (
            <div style={{ gridColumn: '1/3', textAlign: 'center', padding: 40, color: 'var(--color-ink-400)' }}>
              <div style={{ fontSize: 32, marginBottom: 12 }}>◫</div>
              <div style={{ fontSize: 14, marginBottom: 8 }}>创建你的第一个画布</div>
              <button className="feat-btn primary" onClick={handleCreate}>+ 新建画布</button>
            </div>
          )}
        </div>
      </div>
    );
  }

  // --- Canvas editor view ---
  const nodeMap = Object.fromEntries(nodes.map(n => [n.id, n]));

  return (
    <div className="cv-root" style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      {/* Top bar */}
      <div className="cv-top" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '8px 14px', borderBottom: '1px solid var(--color-border)', flexShrink: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <button className="feat-btn subtle small" onClick={() => { setShowList(true); loadCanvases(); }}>←</button>
          <input
            style={{ fontSize: 14, fontWeight: 600, background: 'transparent', border: 'none', outline: 'none', padding: '4px 6px', borderRadius: 4, color: 'var(--color-text)' }}
            value={currentCanvas?.title || ''} onChange={e => setCurrentCanvas(currentCanvas ? { ...currentCanvas, title: e.target.value } : null)}
          />
          <span className="feat-badge">{nodes.length} 节点 · {edges.length} 连线</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <button className={`cv-toolbtn ${tool === 'select' ? 'active' : ''}`} onClick={() => { setTool('select'); setEdgeStart(null); }} title="选择/拖拽 (空白处拖拽平移)">↖</button>
          <button className="cv-toolbtn" onClick={addNode} title="添加空白卡片">▭</button>
          <button className="cv-toolbtn" onClick={() => setShowNoteImport(true)} title="导入笔记">📄</button>
          <button className={`cv-toolbtn ${tool === 'edge' ? 'active' : ''}`} onClick={() => { setTool('edge'); setEdgeStart(null); }} title="连线模式">⤳</button>
          <div style={{ width: 1, height: 20, background: 'var(--color-border)', margin: '0 6px' }} />
          <button className="cv-toolbtn" onClick={handleAiGenerate} disabled={aiLoading} title="AI 分析笔记自动生成画布" style={{ fontSize: 11, width: 'auto', padding: '0 8px' }}>
            {aiLoading ? '⏳ AI 分析中...' : '✦ AI 生成'}
          </button>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          {tool === 'edge' && <span style={{ fontSize: 11, color: 'var(--color-accent)', background: 'rgba(184,69,46,0.08)', padding: '2px 8px', borderRadius: 4 }}>{edgeStart ? '点击目标节点' : '点击起始节点'}</span>}
          <span style={{ fontSize: 11, color: 'var(--color-ink-400)' }}>{Math.round(zoom * 100)}%</span>
          <button className="feat-btn small ghost" onClick={handleSave}>💾 保存</button>
        </div>
      </div>

      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        {/* Canvas stage */}
        <div ref={stageRef} style={{ flex: 1, overflow: 'hidden', position: 'relative', cursor: tool === 'edge' ? 'crosshair' : 'grab', background: 'var(--color-paper-1, #f7f1e3)' }}
          onMouseDown={handleStagePan}
        >
          {/* Grid pattern */}
          <div className="cv-grid" style={{ position: 'absolute', inset: 0, backgroundImage: 'radial-gradient(circle, var(--color-border) 1px, transparent 1px)', backgroundSize: `${20 * zoom}px ${20 * zoom}px`, backgroundPosition: `${pan.x % (20 * zoom)}px ${pan.y % (20 * zoom)}px`, opacity: 0.4, pointerEvents: 'none' }} />

          <div style={{ transform: `translate(${pan.x}px, ${pan.y}px) scale(${zoom})`, transformOrigin: '0 0', position: 'absolute', top: 0, left: 0 }}>
            {/* Edges */}
            <svg style={{ position: 'absolute', top: -2000, left: -2000, width: 6000, height: 6000, pointerEvents: 'none' }}>
              <defs>
                <marker id="cv-arr2" viewBox="0 0 8 8" refX="7" refY="4" markerWidth="8" markerHeight="8" orient="auto">
                  <path d="M0,0 L8,4 L0,8 z" fill="#9a8b73" />
                </marker>
              </defs>
              {edges.map((edge, i) => {
                const a = nodeMap[edge.from], b = nodeMap[edge.to];
                if (!a || !b) return null;
                // +2000 偏移：SVG 定位在 (-2000, -2000)，坐标需补偿
                const ox = 2000, oy = 2000;
                const x1 = a.x + a.w + ox, y1 = a.y + a.h / 2 + oy;
                const x2 = b.x + ox, y2 = b.y + b.h / 2 + oy;
                const dx = (x2 - x1) * 0.4;
                const mx = (x1 + x2) / 2, my = (y1 + y2) / 2;
                return (
                  <g key={i} style={{ pointerEvents: 'all', cursor: 'pointer' }} onClick={async (e) => { e.stopPropagation(); if (await askConfirm({ title: '删除连线', message: `删除连线"${edge.label || '无标签'}"？`, confirmLabel: '删除', danger: true })) setEdges(prev => prev.filter((_, j) => j !== i)); }}>
                    <path d={`M ${x1} ${y1} C ${x1 + dx} ${y1}, ${x2 - dx} ${y2}, ${x2} ${y2}`} stroke="transparent" strokeWidth="14" fill="none" />
                    <path d={`M ${x1} ${y1} C ${x1 + dx} ${y1}, ${x2 - dx} ${y2}, ${x2} ${y2}`} stroke="#9a8b73" strokeWidth="1.5" fill="none" markerEnd="url(#cv-arr2)" />
                    {edge.label && <text x={mx} y={my - 6} textAnchor="middle" fill="#9a8b73" fontSize="11" style={{ pointerEvents: 'none' }}>{edge.label}</text>}
                  </g>
                );
              })}
            </svg>

            {/* Nodes */}
            {nodes.map(n => (
              <div key={n.id}
                style={{
                  position: 'absolute', left: n.x, top: n.y, width: n.w, minHeight: n.h,
                  background: 'var(--color-paper-0, #fbf7ee)',
                  border: `1.5px solid ${active === n.id ? n.color : edgeStart === n.id ? '#b8452e' : 'var(--color-border)'}`,
                  borderRadius: 10, overflow: 'hidden', display: 'flex', flexDirection: 'column',
                  boxShadow: active === n.id ? `0 0 0 2px ${n.color}40, 0 6px 20px rgba(74,64,50,0.12)` : '0 1px 3px rgba(74,64,50,0.08)',
                  cursor: tool === 'edge' ? 'crosshair' : 'grab', userSelect: 'none', transition: 'box-shadow .1s',
                }}
                onMouseDown={e => handleNodeMouseDown(e, n.id)}
                onMouseUp={e => { if (tool === 'edge') { e.stopPropagation(); handleNodeClick(n.id); } }}
                onClick={e => { e.stopPropagation(); if (tool !== 'edge') setActive(n.id); }}
                onDoubleClick={e => { e.stopPropagation(); if (tool !== 'edge') setEditingNode(n.id); }}
              >
                <div style={{ height: 4, background: n.color, flexShrink: 0 }} />
                {n.group && <div style={{ padding: '3px 10px 0', fontSize: 10, color: n.color, fontWeight: 500 }}>{n.group}</div>}
                <div style={{ padding: '8px 12px', flex: 1, display: 'flex', flexDirection: 'column', gap: 4 }}>
                  {editingNode === n.id ? (
                    <div ref={editingNode === n.id ? editContainerRef : undefined}
                      onBlur={(e) => {
                        // 只有焦点离开整个编辑区域才退出编辑
                        const container = editContainerRef.current;
                        if (container && container.contains(e.relatedTarget as Node)) return;
                        setEditingNode(null);
                      }}
                      style={{ display: 'flex', flexDirection: 'column', gap: 4 }}
                    >
                      <input autoFocus className="feat-input" value={n.title}
                        onChange={e => setNodes(prev => prev.map(nd => nd.id === n.id ? { ...nd, title: e.target.value } : nd))}
                        onKeyDown={e => { if (e.key === 'Escape') setEditingNode(null); }}
                        style={{ fontSize: 13, fontWeight: 600, padding: '2px 4px' }}
                      />
                      <textarea className="feat-input" value={n.preview} rows={3}
                        onChange={e => setNodes(prev => prev.map(nd => nd.id === n.id ? { ...nd, preview: e.target.value } : nd))}
                        onKeyDown={e => { if (e.key === 'Escape') setEditingNode(null); }}
                        style={{ fontSize: 11, padding: '2px 4px', resize: 'none' }}
                      />
                    </div>
                  ) : (
                    <>
                      <div style={{ fontSize: 13, fontWeight: 600, lineHeight: 1.4 }}>{n.title}</div>
                      <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', lineHeight: 1.5, flex: 1, overflow: 'hidden', display: '-webkit-box', WebkitLineClamp: 4, WebkitBoxOrient: 'vertical' }}>{n.preview}</div>
                    </>
                  )}
                  {n.tags.length > 0 && (
                    <div style={{ display: 'flex', gap: 3, flexWrap: 'wrap' }}>
                      {n.tags.slice(0, 3).map(t => <span key={t} style={{ fontSize: 9, padding: '1px 5px', background: 'var(--color-paper-2)', borderRadius: 3, color: 'var(--color-text-secondary)' }}>#{t}</span>)}
                    </div>
                  )}
                </div>
                {n.noteId && <div style={{ padding: '0 10px 4px', fontSize: 9, color: 'var(--color-ink-400)' }}>📄 笔记</div>}
              </div>
            ))}
          </div>

          {/* Help text */}
          {nodes.length === 0 && !aiLoading && (
            <div style={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -50%)', textAlign: 'center', color: 'var(--color-ink-400)', pointerEvents: 'none' }}>
              <div style={{ fontSize: 28, marginBottom: 8 }}>◫</div>
              <div style={{ fontSize: 13 }}>点击「✦ AI 生成」自动分析笔记，或手动添加卡片</div>
              <div style={{ fontSize: 11, marginTop: 4 }}>滚轮缩放 · 空白处拖拽平移 · 双击卡片编辑</div>
            </div>
          )}
        </div>

        {/* Side panel */}
        <div style={{ width: 240, borderLeft: '1px solid var(--color-border)', background: 'var(--color-paper-0)', flexShrink: 0, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
          <div style={{ padding: '10px 12px', fontWeight: 600, fontSize: 12, borderBottom: '1px solid var(--color-border)' }}>节点列表</div>
          <div style={{ flex: 1, overflowY: 'auto', padding: 6 }}>
            {nodes.map(n => (
              <div key={n.id} onClick={() => setActive(n.id)}
                style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '6px 8px', borderRadius: 5, fontSize: 11, cursor: 'pointer', background: active === n.id ? 'rgba(184,69,46,0.06)' : 'transparent', marginBottom: 1 }}>
                <span style={{ width: 8, height: 8, borderRadius: 2, background: n.color, flexShrink: 0 }} />
                <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{n.title}</span>
                <button onClick={e => { e.stopPropagation(); deleteNode(n.id); }} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--color-ink-400)', fontSize: 12, padding: 0 }}>×</button>
              </div>
            ))}
          </div>
          {active && nodeMap[active] && (
            <div style={{ borderTop: '1px solid var(--color-border)', padding: '10px 12px' }}>
              <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', marginBottom: 4 }}>颜色</div>
              <div style={{ display: 'flex', gap: 5 }}>
                {COLORS.map(c => (
                  <span key={c} onClick={() => setNodes(prev => prev.map(n => n.id === active ? { ...n, color: c } : n))}
                    style={{ width: 18, height: 18, borderRadius: 5, background: c, cursor: 'pointer', border: nodeMap[active]?.color === c ? '2px solid var(--color-text)' : '2px solid transparent' }} />
                ))}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Note import modal */}
      {showNoteImport && (
        <div className="lp-modal-bg" onClick={() => setShowNoteImport(false)}>
          <div className="lp-modal" onClick={e => e.stopPropagation()} style={{ maxWidth: 400, maxHeight: '70vh' }}>
            <div className="lp-modal-head">
              <span style={{ fontWeight: 600 }}>导入笔记</span>
              <button className="feat-btn subtle small" onClick={() => setShowNoteImport(false)}>×</button>
            </div>
            <div style={{ padding: '8px 16px', height: noteImportHeight }}>
              <FixedSizeList
                height={noteImportHeight}
                width="100%"
                itemCount={notes.length}
                itemSize={NOTE_IMPORT_ROW_HEIGHT}
                itemKey={(index) => notes[index].id}
              >
                {renderNoteImportRow}
              </FixedSizeList>
            </div>
            <div className="lp-modal-foot"><button className="feat-btn ghost" onClick={() => setShowNoteImport(false)}>关闭</button></div>
          </div>
        </div>
      )}
    </div>
  );
}
