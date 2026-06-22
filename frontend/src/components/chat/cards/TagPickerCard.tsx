import { useState } from 'react';
import './ai-cards.css';

interface Props {
  title: string;
  noteName: string;
  tags: string[];
  defaultSelected?: string[];
  collapsed?: boolean;
  result?: string;
  onConfirm: (selected: string[]) => void;
  onCancel: () => void;
}

export default function TagPickerCard({
  title, noteName, tags, defaultSelected = [],
  collapsed, result, onConfirm, onCancel,
}: Props) {
  const [sel, setSel] = useState<Record<string, boolean>>(() => {
    const init: Record<string, boolean> = {};
    tags.forEach(t => { init[t] = defaultSelected.includes(t); });
    return init;
  });

  const count = Object.values(sel).filter(Boolean).length;
  const toggle = (tag: string) => setSel(s => ({ ...s, [tag]: !s[tag] }));

  if (collapsed) {
    return (
      <div className="cc-card collapsed">
        <div className="cc-head">
          <div className="cc-head-ico" style={{ background: 'rgba(184,69,46,0.1)', color: 'var(--color-accent, #b8452e)' }}>#</div>
          <div className="cc-head-text"><div className="cc-head-title">{title}</div></div>
        </div>
        <div className="cc-collapsed-summary">
          <span className="cc-done-icon">✓</span>
          <span>{result || '已完成'}</span>
        </div>
      </div>
    );
  }

  return (
    <div className="cc-card">
      <div className="cc-head">
        <div className="cc-head-ico" style={{ background: 'rgba(184,69,46,0.1)', color: 'var(--color-accent, #b8452e)' }}>#</div>
        <div className="cc-head-text">
          <div className="cc-head-title">给「{noteName}」添加标签</div>
          <div className="cc-head-meta">已选 {count} 个</div>
        </div>
      </div>
      <div className="cc-body">
        <div className="cc-chip-grid">
          {tags.map(t => (
            <span key={t} className={`cc-chip ${sel[t] ? 'checked' : ''}`} onClick={() => toggle(t)}>
              <span className="cc-chip-mark">✓</span>
              #{t}
            </span>
          ))}
        </div>
        <div className="cc-newtag-input">
          <span style={{ color: 'var(--color-accent, #b8452e)' }}>+</span>
          <span>输入新标签名…</span>
        </div>
      </div>
      <div className="cc-actions">
        <button className="cc-btn subtle" onClick={onCancel}>取消</button>
        <button className="cc-btn primary" onClick={() => onConfirm(Object.keys(sel).filter(k => sel[k]))}>
          确认添加 ({count})
        </button>
      </div>
    </div>
  );
}
