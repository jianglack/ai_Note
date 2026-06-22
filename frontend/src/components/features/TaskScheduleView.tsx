import { useEffect, useState, useCallback } from 'react';
import {
  getTaskSchedules, createTaskSchedule, toggleTaskSchedule, deleteTaskSchedule,
  type TaskScheduleData,
} from '../../api';
import { askConfirm } from '../../services/dialogService';

const T = {
  bg: 'var(--color-paper-0, #fbf7ee)',
  surface: 'var(--color-paper-1, #f5efe0)',
  border: 'rgba(74,64,50,0.12)',
  borderStrong: 'rgba(74,64,50,0.22)',
  text: 'var(--color-text, #2b2620)',
  textSecondary: 'var(--color-text-secondary, #7a6b58)',
  accent: 'var(--color-accent, #b8452e)',
  success: '#6b7a5a',
  font: '-apple-system, "PingFang SC", "Noto Sans SC", "Helvetica Neue", sans-serif',
};

const TRIGGER_LABELS: Record<string, string> = {
  ONCE: '一次性',
  DAILY: '每日',
  WEEKLY: '每周',
  MONTHLY: '每月',
};

export default function TaskScheduleView({ onClose }: { onClose: () => void }) {
  const [schedules, setSchedules] = useState<TaskScheduleData[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);

  // Form state
  const [query, setQuery] = useState('');
  const [triggerType, setTriggerType] = useState('ONCE');
  const [scheduledTime, setScheduledTime] = useState('');
  const [maxRunCount, setMaxRunCount] = useState('');

  const loadSchedules = useCallback(async () => {
    try {
      const data = await getTaskSchedules();
      setSchedules(data);
    } catch { /* ignore */ }
    setLoading(false);
  }, []);

  useEffect(() => { loadSchedules(); }, [loadSchedules]);

  const handleCreate = async () => {
    if (!query.trim()) return;
    try {
      await createTaskSchedule(
        query,
        triggerType,
        scheduledTime || undefined,
        maxRunCount ? parseInt(maxRunCount) : undefined
      );
      setQuery('');
      setScheduledTime('');
      setMaxRunCount('');
      setShowForm(false);
      loadSchedules();
    } catch (err) {
      console.error('Create schedule failed:', err);
    }
  };

  const handleToggle = async (id: string) => {
    try {
      await toggleTaskSchedule(id);
      loadSchedules();
    } catch { /* ignore */ }
  };

  const handleDelete = async (id: string) => {
    const confirmed = await askConfirm({
      title: 'Delete scheduled task',
      message: 'This scheduled task will be permanently deleted.',
      confirmLabel: 'Delete',
      danger: true,
    });
    if (!confirmed) return;

    try {
      await deleteTaskSchedule(id);
      setSchedules(prev => prev.filter(s => s.id !== id));
    } catch { /* ignore */ }
  };

  return (
    <div style={{
      position: 'fixed', inset: 0, zIndex: 60,
      background: T.bg, fontFamily: T.font, display: 'flex', flexDirection: 'column',
    }}>
      {/* Header */}
      <div style={{
        display: 'flex', alignItems: 'center', gap: 12, padding: '14px 24px',
        borderBottom: `1px solid ${T.borderStrong}`, flexShrink: 0,
      }}>
        <span style={{ fontSize: 18, fontWeight: 700, color: T.text, flex: 1 }}>
          定时任务
        </span>
        <button onClick={() => setShowForm(!showForm)} style={{
          background: T.accent + '18', color: T.accent, border: `1px solid ${T.accent}44`,
          borderRadius: 8, padding: '6px 14px', fontSize: 13, fontWeight: 600, cursor: 'pointer',
        }}>
          {showForm ? '取消' : '+ 新建'}
        </button>
        <button type="button" aria-label="Close task schedule" onClick={onClose} style={{
          background: 'none', border: `1px solid ${T.border}`, borderRadius: 8,
          padding: '6px 14px', fontSize: 13, cursor: 'pointer', color: T.textSecondary,
        }}>
          关闭
        </button>
      </div>

      {/* Create form */}
      {showForm && (
        <div style={{
          padding: '16px 24px', borderBottom: `1px solid ${T.border}`,
          background: T.surface, display: 'flex', flexDirection: 'column', gap: 12,
        }}>
          <div>
            <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>
              任务描述
            </label>
            <input
              value={query} onChange={e => setQuery(e.target.value)}
              placeholder="例如：整理本周笔记并生成摘要"
              style={{
                width: '100%', padding: '8px 12px', fontSize: 14, borderRadius: 8,
                border: `1px solid ${T.borderStrong}`, background: T.bg, color: T.text,
                fontFamily: T.font, boxSizing: 'border-box',
              }}
            />
          </div>
          <div style={{ display: 'flex', gap: 12 }}>
            <div style={{ flex: 1 }}>
              <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>
                触发类型
              </label>
              <select value={triggerType} onChange={e => setTriggerType(e.target.value)} style={{
                width: '100%', padding: '8px 12px', fontSize: 14, borderRadius: 8,
                border: `1px solid ${T.borderStrong}`, background: T.bg, color: T.text,
              }}>
                <option value="ONCE">一次性</option>
                <option value="DAILY">每日</option>
                <option value="WEEKLY">每周</option>
                <option value="MONTHLY">每月</option>
              </select>
            </div>
            <div style={{ flex: 1 }}>
              <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>
                执行时间
              </label>
              <input type="datetime-local" value={scheduledTime}
                onChange={e => setScheduledTime(e.target.value)}
                style={{
                  width: '100%', padding: '8px 12px', fontSize: 14, borderRadius: 8,
                  border: `1px solid ${T.borderStrong}`, background: T.bg, color: T.text,
                  boxSizing: 'border-box',
                }}
              />
            </div>
            {triggerType !== 'ONCE' && (
              <div style={{ width: 100 }}>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>
                  最大次数
                </label>
                <input type="number" value={maxRunCount}
                  onChange={e => setMaxRunCount(e.target.value)}
                  placeholder="无限"
                  style={{
                    width: '100%', padding: '8px 12px', fontSize: 14, borderRadius: 8,
                    border: `1px solid ${T.borderStrong}`, background: T.bg, color: T.text,
                    boxSizing: 'border-box',
                  }}
                />
              </div>
            )}
          </div>
          <button onClick={handleCreate} style={{
            background: T.accent, color: '#fff', border: 'none', borderRadius: 8,
            padding: '8px 20px', fontSize: 14, fontWeight: 600, cursor: 'pointer', alignSelf: 'flex-end',
          }}>
            创建
          </button>
        </div>
      )}

      {/* Schedule list */}
      <div style={{ flex: 1, overflowY: 'auto', padding: 24 }}>
        {loading && <div style={{ color: T.textSecondary, fontSize: 14 }}>加载中...</div>}
        {!loading && schedules.length === 0 && (
          <div style={{ color: T.textSecondary, fontSize: 14, textAlign: 'center', marginTop: 40 }}>
            暂无定时任务。点击「新建」创建你的第一个自动化任务。
          </div>
        )}
        <div style={{ display: 'grid', gap: 12, maxWidth: 800 }}>
          {schedules.map(s => (
            <div key={s.id} style={{
              background: T.surface, border: `1px solid ${T.border}`, borderRadius: 12,
              padding: 16, display: 'flex', alignItems: 'center', gap: 14,
            }}>
              {/* Toggle */}
              <button onClick={() => handleToggle(s.id)} style={{
                width: 40, height: 22, borderRadius: 11, border: 'none', cursor: 'pointer',
                background: s.enabled ? T.success : T.border, position: 'relative', transition: 'background 0.2s',
                flexShrink: 0,
              }}>
                <span style={{
                  width: 16, height: 16, borderRadius: '50%', background: '#fff',
                  position: 'absolute', top: 3, transition: 'left 0.2s',
                  left: s.enabled ? 21 : 3,
                }} />
              </button>

              {/* Info */}
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{
                  fontSize: 14, fontWeight: 600, color: s.enabled ? T.text : T.textSecondary,
                  overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                }}>
                  {s.originalQuery}
                </div>
                <div style={{ fontSize: 12, color: T.textSecondary, marginTop: 4, display: 'flex', gap: 12 }}>
                  <span>{TRIGGER_LABELS[s.triggerType] || s.triggerType}</span>
                  {s.nextRunAt && <span>下次: {new Date(s.nextRunAt).toLocaleString()}</span>}
                  <span>已运行 {s.runCount} 次</span>
                </div>
              </div>

              {/* Delete */}
              <button onClick={() => handleDelete(s.id)} style={{
                background: 'none', border: `1px solid ${T.border}`, borderRadius: 6,
                padding: '4px 10px', fontSize: 12, cursor: 'pointer', color: '#c44',
              }}>
                删除
              </button>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
