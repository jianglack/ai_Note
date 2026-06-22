import { useState } from 'react';
import type { MultiSelectItem } from './types';
import './ai-cards.css';

interface Props {
  title: string;
  meta?: string;
  icon?: string;
  items: MultiSelectItem[];
  defaultSelected?: string[];
  confirmLabel?: string;
  collapsed?: boolean;
  result?: string;
  onConfirm: (selectedIds: string[]) => void;
  onCancel: () => void;
}

export default function MultiSelectCard({
  title, meta, icon = '📁', items, defaultSelected,
  confirmLabel = '确认', collapsed, result, onConfirm, onCancel,
}: Props) {
  const [sel, setSel] = useState<Record<string, boolean>>(() => {
    const init: Record<string, boolean> = {};
    items.forEach(i => { init[i.id] = defaultSelected?.includes(i.id) ?? false; });
    return init;
  });

  const count = Object.values(sel).filter(Boolean).length;
  const toggle = (id: string) => setSel(s => ({ ...s, [id]: !s[id] }));
  const selectAll = () => setSel(Object.fromEntries(items.map(i => [i.id, true])));
  const clearAll = () => setSel(Object.fromEntries(items.map(i => [i.id, false])));

  if (collapsed) {
    return (
      <div className="cc-card collapsed">
        <div className="cc-head">
          <div className="cc-head-ico" style={{ background: 'rgba(184,69,46,0.1)', color: 'var(--color-accent, #b8452e)' }}>{icon}</div>
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
        <div className="cc-head-ico" style={{ background: 'rgba(184,69,46,0.1)', color: 'var(--color-accent, #b8452e)' }}>{icon}</div>
        <div className="cc-head-text">
          <div className="cc-head-title">{title}</div>
          {meta && <div className="cc-head-meta">{meta}</div>}
        </div>
      </div>
      <div className="cc-toolrow">
        <span className="cc-toolrow-status">已选 <span className="cc-toolrow-tag">{count}/{items.length}</span></span>
        <div className="cc-toolrow-actions">
          <button className="cc-link-btn" onClick={selectAll}>全选</button>
          <button className="cc-link-btn" onClick={clearAll}>清空</button>
        </div>
      </div>
      <div className="cc-list">
        {items.map(i => (
          <div key={i.id} className={`cc-list-row ${sel[i.id] ? 'checked' : ''}`} onClick={() => toggle(i.id)}>
            <span className="cc-check">✓</span>
            <span className="cc-list-name">{i.name}</span>
            {i.tag && <span className="cc-list-tag">{i.tag}</span>}
          </div>
        ))}
      </div>
      <div className="cc-actions">
        <button className="cc-btn subtle" onClick={onCancel}>取消</button>
        <button className="cc-btn primary" onClick={() => onConfirm(Object.keys(sel).filter(k => sel[k]))}>
          {confirmLabel} ({count})
        </button>
      </div>
    </div>
  );
}
