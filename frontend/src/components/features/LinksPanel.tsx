import { useState, useEffect } from 'react';
import { getTypedLinksByNote, createTypedLink, deleteTypedLink, searchNotes } from '../../api';
import { useNoteStore } from '../../stores/noteStore';
import './features.css';

const REL_TYPES = [
  { id: 'supports', ico: '🔗', label: '支持', color: 'good', desc: '佐证、强化目标观点' },
  { id: 'contradicts', ico: '⚡', label: '矛盾', color: 'danger', desc: '与目标观点冲突' },
  { id: 'implements', ico: '🔧', label: '实现', color: 'info', desc: '把目标想法落地' },
  { id: 'depends_on', ico: '📌', label: '依赖', color: 'warn', desc: '需要先理解目标' },
  { id: 'related', ico: '🔄', label: '相关', color: 'muted', desc: '主题相关' },
];

interface TypedLink {
  id: string;
  sourceNoteId: string;
  targetNoteId: string;
  relationType: string;
  context?: string;
  sourceTitle?: string;
  targetTitle?: string;
}

export default function LinksPanel() {
  const { selectedNote, notes } = useNoteStore();
  const [links, setLinks] = useState<TypedLink[]>([]);
  const [showAdd, setShowAdd] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchHits, setSearchHits] = useState<{ id: string; title: string }[]>([]);
  const [selectedTarget, setSelectedTarget] = useState<string | null>(null);
  const [selectedRel, setSelectedRel] = useState('supports');
  const [direction, setDirection] = useState<'out' | 'in'>('out');
  const [context, setContext] = useState('');

  useEffect(() => {
    if (selectedNote?.id) {
      loadLinks();
    }
  }, [selectedNote?.id]);

  const loadLinks = async () => {
    if (!selectedNote?.id) return;
    try {
      const data = await getTypedLinksByNote(selectedNote.id);
      // Enrich with titles
      const enriched = data.map((l: any) => ({
        ...l,
        sourceTitle: notes.find(n => n.id === l.sourceNoteId)?.title || '未知笔记',
        targetTitle: notes.find(n => n.id === l.targetNoteId)?.title || '未知笔记',
      }));
      setLinks(enriched);
    } catch (e) {
      console.error('Failed to load links', e);
    }
  };

  const handleSearch = async (q: string) => {
    setSearchQuery(q);
    if (q.length < 1) { setSearchHits([]); return; }
    try {
      const results = await searchNotes(q);
      setSearchHits(results.filter((n: any) => n.id !== selectedNote?.id).slice(0, 5).map((n: any) => ({ id: n.id, title: n.title })));
    } catch { setSearchHits([]); }
  };

  const handleAdd = async () => {
    if (!selectedNote?.id || !selectedTarget) return;
    const src = direction === 'out' ? selectedNote.id : selectedTarget;
    const tgt = direction === 'out' ? selectedTarget : selectedNote.id;
    try {
      await createTypedLink(src, tgt, selectedRel, context);
      setShowAdd(false);
      setSearchQuery('');
      setSelectedTarget(null);
      setContext('');
      loadLinks();
    } catch (e) { console.error(e); }
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteTypedLink(id);
      setLinks(links.filter(l => l.id !== id));
    } catch (e) { console.error(e); }
  };

  if (!selectedNote) return null;

  const grouped = REL_TYPES.map(t => ({
    type: t,
    items: links.map(l => {
      const isOut = l.sourceNoteId === selectedNote.id;
      return { ...l, dir: isOut ? 'out' as const : 'in' as const, title: isOut ? l.targetTitle : l.sourceTitle };
    }).filter(l => l.relationType === t.id),
  }));

  return (
    <div className="lp-root">
      <div className="lp-head">
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span className="lp-title">关系链接</span>
          <span className="feat-badge accent">{links.length}</span>
        </div>
        <button className="feat-btn small ghost" onClick={() => setShowAdd(true)}>+</button>
      </div>

      <div className="lp-body">
        {grouped.map(g => g.items.length === 0 ? null : (
          <div key={g.type.id} className="lp-group">
            <div className="lp-group-head">
              <span className={`lp-rel-ico c-${g.type.color}`}>{g.type.ico}</span>
              <span className="lp-group-name">{g.type.label}</span>
              <span className="lp-group-count">{g.items.length}</span>
            </div>
            {g.items.map(l => (
              <div key={l.id} className="lp-link">
                <span className={`lp-rel-bar c-${g.type.color}`}></span>
                <div className="lp-link-body">
                  <div className="lp-link-row">
                    <span className="lp-link-title">{l.title}</span>
                    <span className={`lp-dir ${l.dir}`}>{l.dir === 'out' ? '→' : '←'}</span>
                  </div>
                  {l.context && <div className="lp-link-desc">{l.context}</div>}
                </div>
                <button className="lp-x" onClick={() => handleDelete(l.id)}>×</button>
              </div>
            ))}
          </div>
        ))}

        {links.length === 0 && (
          <div style={{ padding: '20px 14px', textAlign: 'center', color: 'var(--color-ink-400, #9a8b73)', fontSize: 12 }}>
            暂无关系链接
          </div>
        )}

        <button className="lp-add-cta" onClick={() => setShowAdd(true)}>+ 添加新链接</button>
      </div>

      {showAdd && (
        <div className="lp-modal-bg" onClick={() => setShowAdd(false)}>
          <div className="lp-modal" onClick={e => e.stopPropagation()}>
            <div className="lp-modal-head">
              <span style={{ fontWeight: 600 }}>添加关系链接</span>
              <button className="feat-btn subtle small" onClick={() => setShowAdd(false)}>×</button>
            </div>
            <div className="lp-modal-body">
              <div>
                <label style={{ fontSize: 11, color: 'var(--color-text-secondary)', display: 'block', marginBottom: 5 }}>搜索目标笔记</label>
                <input className="feat-input" placeholder="输入笔记标题…" value={searchQuery} onChange={e => handleSearch(e.target.value)} />
                {searchHits.length > 0 && (
                  <div className="lp-search-hits">
                    {searchHits.map(h => (
                      <div key={h.id} className={`lp-hit ${selectedTarget === h.id ? 'on' : ''}`} onClick={() => { setSelectedTarget(h.id); setSearchQuery(h.title); setSearchHits([]); }}>
                        {h.title}
                      </div>
                    ))}
                  </div>
                )}
              </div>
              <div>
                <label style={{ fontSize: 11, color: 'var(--color-text-secondary)', display: 'block', marginBottom: 5 }}>关系类型</label>
                <div className="lp-rel-grid">
                  {REL_TYPES.map(t => (
                    <button key={t.id} className={`lp-rel-card ${selectedRel === t.id ? 'on' : ''}`} onClick={() => setSelectedRel(t.id)}>
                      <span className={`lp-rel-ico c-${t.color}`}>{t.ico}</span>
                      <div className="lp-rel-meta">
                        <div className="lp-rel-name">{t.label}</div>
                        <div className="lp-rel-desc">{t.desc}</div>
                      </div>
                    </button>
                  ))}
                </div>
              </div>
              <div>
                <label style={{ fontSize: 11, color: 'var(--color-text-secondary)', display: 'block', marginBottom: 5 }}>描述（可选）</label>
                <input className="feat-input" placeholder="为什么建立这条链接？" value={context} onChange={e => setContext(e.target.value)} />
              </div>
              <div>
                <label style={{ fontSize: 11, color: 'var(--color-text-secondary)', display: 'block', marginBottom: 5 }}>方向</label>
                <div className="lp-dir-toggle">
                  <button className={direction === 'out' ? 'on' : ''} onClick={() => setDirection('out')}>当前笔记 → 目标</button>
                  <button className={direction === 'in' ? 'on' : ''} onClick={() => setDirection('in')}>目标 → 当前笔记</button>
                </div>
              </div>
            </div>
            <div className="lp-modal-foot">
              <button className="feat-btn ghost" onClick={() => setShowAdd(false)}>取消</button>
              <button className="feat-btn primary" onClick={handleAdd} disabled={!selectedTarget}>添加链接</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
