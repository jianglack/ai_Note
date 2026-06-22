import { useState } from 'react';
import type { SingleSelectItem } from './types';
import './ai-cards.css';

interface Props {
  title: string;
  meta?: string;
  icon?: string;
  items: SingleSelectItem[];
  defaultSelected?: string;
  collapsed?: boolean;
  result?: string;
  onConfirm: (id: string) => void;
  onCancel: () => void;
  onCreate?: () => void;
}

export default function SingleSelectCard({
  title, meta, icon = '📁', items, defaultSelected,
  collapsed, result, onConfirm, onCancel, onCreate,
}: Props) {
  const [selected, setSelected] = useState(defaultSelected || '');

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
      <div className="cc-list">
        {items.map(i => (
          <div key={i.id} className={`cc-list-row ${selected === i.id ? 'checked' : ''}`} onClick={() => setSelected(i.id)}>
            <span className="cc-radio" />
            <span className="cc-list-name">{i.name}</span>
            {i.recommend && <span className="cc-recommend">✦ 推荐</span>}
          </div>
        ))}
        {onCreate && (
          <>
            <div className="cc-divider" />
            <div className="cc-list-row add" onClick={onCreate}>
              <span className="cc-plus">+</span>
              <span className="cc-list-name">新建笔记本…</span>
            </div>
          </>
        )}
      </div>
      <div className="cc-actions">
        <button className="cc-btn subtle" onClick={onCancel}>取消</button>
        <button className="cc-btn primary" onClick={() => selected && onConfirm(selected)}>确认</button>
      </div>
    </div>
  );
}
