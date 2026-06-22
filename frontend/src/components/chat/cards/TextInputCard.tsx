import { useState } from 'react';
import './ai-cards.css';

interface Props {
  title?: string;
  meta?: string;
  initialTitle?: string;
  initialNotebook?: { id: string; name: string };
  initialTags?: string[];
  collapsed?: boolean;
  result?: string;
  onConfirm: (data: { title: string; notebookId?: string; tags: string[] }) => void;
  onCancel: () => void;
}

export default function TextInputCard({
  title = '创建新笔记', meta, initialTitle = '', initialNotebook,
  initialTags = [], collapsed, result, onConfirm, onCancel,
}: Props) {
  const [noteTitle, setNoteTitle] = useState(initialTitle);
  const [tags, setTags] = useState<string[]>(initialTags);

  if (collapsed) {
    return (
      <div className="cc-card collapsed">
        <div className="cc-head">
          <div className="cc-head-ico" style={{ background: 'rgba(107,163,104,0.12)', color: '#6b7a5a' }}>📝</div>
          <div className="cc-head-text"><div className="cc-head-title">{title}</div></div>
        </div>
        <div className="cc-collapsed-summary">
          <span className="cc-done-icon">✓</span>
          <span>{result || '已完成'}</span>
        </div>
      </div>
    );
  }

  const removeTag = (tag: string) => setTags(tags.filter(t => t !== tag));

  return (
    <div className="cc-card">
      <div className="cc-head">
        <div className="cc-head-ico" style={{ background: 'rgba(107,163,104,0.12)', color: '#6b7a5a' }}>📝</div>
        <div className="cc-head-text">
          <div className="cc-head-title">{title}</div>
          {meta && <div className="cc-head-meta">{meta}</div>}
        </div>
      </div>
      <div className="cc-body">
        <div className="cc-field">
          <label className="cc-label">标题</label>
          <input className="cc-input" value={noteTitle} onChange={e => setNoteTitle(e.target.value)} placeholder="输入笔记标题…" />
        </div>
        <div className="cc-grid2">
          <div className="cc-field">
            <label className="cc-label">笔记本</label>
            <div className="cc-select-display">
              <span>📁 {initialNotebook?.name || '未分类'}</span>
              <span className="cc-caret">▾</span>
            </div>
          </div>
          <div className="cc-field">
            <label className="cc-label">标签</label>
            <div className="cc-tag-row">
              {tags.map(t => (
                <span key={t} className="cc-tag-pill">#{t} <span className="cc-tag-x" onClick={() => removeTag(t)}>×</span></span>
              ))}
              <button className="cc-tag-add">+ 添加</button>
            </div>
          </div>
        </div>
      </div>
      <div className="cc-actions">
        <button className="cc-btn subtle" onClick={onCancel}>取消</button>
        <button className="cc-btn primary" onClick={() => onConfirm({ title: noteTitle, notebookId: initialNotebook?.id, tags })}>
          创建笔记
        </button>
      </div>
    </div>
  );
}
