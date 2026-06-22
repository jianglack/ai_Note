import { useState } from 'react';
import './ai-cards.css';

interface Props {
  title?: string;
  meta?: string;
  initial: { title: string; date: string; time: string; remind?: string };
  collapsed?: boolean;
  result?: string;
  onConfirm: (data: { title: string; date: string; time: string; remind: string }) => void;
  onCancel: () => void;
}

export default function ScheduleCard({
  title = '创建日程', meta, initial,
  collapsed, result, onConfirm, onCancel,
}: Props) {
  const [schedTitle, setSchedTitle] = useState(initial.title);
  const [date, setDate] = useState(initial.date);
  const [time, setTime] = useState(initial.time);
  const [remind, setRemind] = useState(initial.remind || '30');

  if (collapsed) {
    return (
      <div className="cc-card collapsed">
        <div className="cc-head">
          <div className="cc-head-ico" style={{ background: 'rgba(107,163,104,0.12)', color: '#6b7a5a' }}>📅</div>
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
        <div className="cc-head-ico" style={{ background: 'rgba(107,163,104,0.12)', color: '#6b7a5a' }}>📅</div>
        <div className="cc-head-text">
          <div className="cc-head-title">{title}</div>
          {meta && <div className="cc-head-meta">{meta}</div>}
        </div>
      </div>
      <div className="cc-body">
        <div className="cc-field">
          <label className="cc-label">标题</label>
          <input className="cc-input" value={schedTitle} onChange={e => setSchedTitle(e.target.value)} />
        </div>
        <div className="cc-grid2">
          <div className="cc-field">
            <label className="cc-label">日期</label>
            <input className="cc-input" type="date" value={date} onChange={e => setDate(e.target.value)} />
          </div>
          <div className="cc-field">
            <label className="cc-label">时间</label>
            <input className="cc-input" type="time" value={time} onChange={e => setTime(e.target.value)} />
          </div>
        </div>
        <div className="cc-field">
          <label className="cc-label">提醒</label>
          <div className="cc-select-display" onClick={() => {
            const options = ['0', '5', '15', '30', '60'];
            const idx = options.indexOf(remind);
            setRemind(options[(idx + 1) % options.length]);
          }}>
            <span>{remind === '0' ? '不提醒' : `提前 ${remind} 分钟`}</span>
            <span className="cc-caret">▾</span>
          </div>
        </div>
      </div>
      <div className="cc-actions">
        <button className="cc-btn subtle" onClick={onCancel}>取消</button>
        <button className="cc-btn primary" onClick={() => onConfirm({ title: schedTitle, date, time, remind })}>
          创建日程
        </button>
      </div>
    </div>
  );
}
