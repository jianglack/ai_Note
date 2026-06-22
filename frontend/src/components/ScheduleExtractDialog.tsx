import { useState, useEffect } from 'react';
import type { ExtractedSchedule, ExtractedScheduleResponse, ScheduleCreateRequest, Schedule } from '../api';
import { extractSchedulesFromNote, createSchedule } from '../api';
import './ScheduleExtractDialog.css';

interface ScheduleExtractDialogProps {
  noteId: string;
  onSave: (schedules: Schedule[]) => void;
  onClose: () => void;
}

function formatTime(startTime: string, endTime?: string, allDay?: boolean) {
  const start = new Date(startTime);
  if (allDay) {
    return start.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' }) + ' (全天)';
  }
  const startStr = start.toLocaleString('zh-CN', {
    year: 'numeric', month: 'long', day: 'numeric',
    hour: '2-digit', minute: '2-digit'
  });
  if (endTime) {
    const end = new Date(endTime);
    const endStr = end.toLocaleString('zh-CN', { hour: '2-digit', minute: '2-digit' });
    return `${startStr} - ${endStr}`;
  }
  return startStr;
}

function formatRrule(rrule: string): string {
  if (!rrule) return '';
  if (rrule.includes('FREQ=DAILY')) return '每天';
  if (rrule.includes('FREQ=WEEKLY')) {
    if (rrule.includes('BYDAY=MO')) return '每周一';
    if (rrule.includes('BYDAY=TU')) return '每周二';
    if (rrule.includes('BYDAY=WE')) return '每周三';
    if (rrule.includes('BYDAY=TH')) return '每周四';
    if (rrule.includes('BYDAY=FR')) return '每周五';
    if (rrule.includes('BYDAY=SA')) return '每周六';
    if (rrule.includes('BYDAY=SU')) return '每周日';
    return '每周';
  }
  if (rrule.includes('FREQ=MONTHLY')) return '每月';
  if (rrule.includes('FREQ=YEARLY')) return '每年';
  return '重复';
}

export default function ScheduleExtractDialog({
  noteId,
  onSave,
  onClose
}: ScheduleExtractDialogProps) {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [schedules, setSchedules] = useState<ExtractedSchedule[]>([]);
  const [selected, setSelected] = useState<Set<number>>(new Set());

  useEffect(() => {
    loadSchedules();
  }, [noteId]);

  const loadSchedules = async () => {
    setLoading(true);
    try {
      const response: ExtractedScheduleResponse = await extractSchedulesFromNote(noteId);
      setSchedules(response.schedules);
      // 默认全选
      setSelected(new Set(response.schedules.map((_, i) => i)));
    } catch (err) {
      console.error('提取日程失败:', err);
      setSchedules([]);
    } finally {
      setLoading(false);
    }
  };

  const toggleSelect = (index: number) => {
    setSelected(prev => {
      const next = new Set(prev);
      if (next.has(index)) {
        next.delete(index);
      } else {
        next.add(index);
      }
      return next;
    });
  };

  const handleSave = async () => {
    if (selected.size === 0) return;

    setSaving(true);
    try {
      const createdSchedules: Schedule[] = [];
      for (const index of selected) {
        const extracted = schedules[index];
        const payload: ScheduleCreateRequest = {
          title: extracted.title,
          startTime: extracted.startTime,
          endTime: extracted.endTime,
          allDay: extracted.allDay,
          rrule: extracted.rrule,
          noteIds: [noteId]
        };
        const created = await createSchedule(payload);
        createdSchedules.push(created);
      }
      onSave(createdSchedules);
    } catch (err) {
      console.error('保存日程失败:', err);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="extract-dialog-overlay" onClick={onClose}>
      <div className="extract-dialog-panel" onClick={e => e.stopPropagation()}>
        <div className="extract-dialog-header">
          <h2>AI 提取日程</h2>
          <button type="button" className="extract-dialog-close" aria-label="Close schedule extraction dialog" onClick={onClose}>✕</button>
        </div>

        <div className="extract-dialog-body">
          {loading ? (
            <div className="extract-loading">
              <div className="extract-spinner" />
              <span>正在分析笔记内容...</span>
            </div>
          ) : schedules.length === 0 ? (
            <div className="extract-empty">
              <div className="extract-empty-icon">📅</div>
              <div>未从笔记中识别到日程信息</div>
            </div>
          ) : (
            <div className="extract-list">
              {schedules.map((schedule, index) => (
                <div
                  key={index}
                  className={`extract-item ${selected.has(index) ? 'selected' : ''}`}
                >
                  <div className="extract-item-header">
                    <input
                      type="checkbox"
                      className="extract-item-checkbox"
                      checked={selected.has(index)}
                      onChange={() => toggleSelect(index)}
                    />
                    <div className="extract-item-content">
                      <div className="extract-item-title">{schedule.title}</div>
                      <div className="extract-item-time">
                        {formatTime(schedule.startTime, schedule.endTime, schedule.allDay)}
                      </div>
                      <div className="extract-item-meta">
                        <span className={`extract-confidence ${schedule.confidence < 0.8 ? 'medium' : ''}`}>
                          置信度 {Math.round(schedule.confidence * 100)}%
                        </span>
                        {schedule.rrule && (
                          <span className="extract-rrule">{formatRrule(schedule.rrule)}</span>
                        )}
                      </div>
                      {schedule.source && (
                        <div className="extract-source">"{schedule.source}"</div>
                      )}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="extract-dialog-footer">
          <span className="extract-select-info">
            已选择 {selected.size} / {schedules.length} 个日程
          </span>
          <div className="extract-dialog-actions">
            <button onClick={onClose} disabled={saving}>取消</button>
            <button
              className="primary"
              onClick={handleSave}
              disabled={saving || selected.size === 0}
            >
              {saving ? '保存中...' : '创建日程'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
