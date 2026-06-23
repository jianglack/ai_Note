import { useId, useState, useEffect, useRef, type FormEvent, type KeyboardEvent } from 'react';
import type { Schedule, Note, ScheduleCreateRequest } from '../api';
import { createSchedule, updateSchedule } from '../api';
import './ScheduleForm.css';

interface ScheduleFormProps {
  schedule?: Schedule;
  notes: Note[];
  onSave: (schedule: Schedule) => void;
  onClose: () => void;
}

const REPEAT_OPTIONS = [
  { value: '', label: '不重复' },
  { value: 'FREQ=DAILY', label: '每天' },
  { value: 'FREQ=WEEKLY', label: '每周' },
  { value: 'FREQ=MONTHLY', label: '每月' },
  { value: 'FREQ=YEARLY', label: '每年' },
];

const REMINDER_OPTIONS = [
  { value: 0, label: '无提醒' },
  { value: 5, label: '5 分钟前' },
  { value: 15, label: '15 分钟前' },
  { value: 30, label: '30 分钟前' },
  { value: 60, label: '1 小时前' },
  { value: 1440, label: '1 天前' },
];

const FOCUSABLE_SELECTOR = [
  'button:not([disabled])',
  '[href]',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',');

function getFocusableElements(container: HTMLElement | null) {
  if (!container) return [];
  return Array.from(container.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR))
    .filter(element => element.tabIndex >= 0);
}

function formatDateTimeLocal(dateStr?: string) {
  if (!dateStr) return '';
  const d = new Date(dateStr.replace(' ', 'T'));
  return d.toISOString().slice(0, 16);
}

function formatDateLocal(dateStr?: string) {
  if (!dateStr) return '';
  const d = new Date(dateStr.replace(' ', 'T'));
  return d.toISOString().slice(0, 10);
}

export default function ScheduleForm({ schedule, notes, onSave, onClose }: ScheduleFormProps) {
  const formId = useId();
  const panelRef = useRef<HTMLDivElement>(null);
  const titleInputRef = useRef<HTMLInputElement>(null);
  const descriptionInputRef = useRef<HTMLTextAreaElement>(null);
  const startTimeInputRef = useRef<HTMLInputElement>(null);
  const endTimeInputRef = useRef<HTMLInputElement>(null);
  const headingId = `${formId}-heading`;
  const titleId = `${formId}-title`;
  const descriptionId = `${formId}-description`;
  const allDayId = `${formId}-all-day`;
  const startTimeId = `${formId}-start-time`;
  const endTimeId = `${formId}-end-time`;
  const rruleId = `${formId}-rrule`;
  const reminderId = `${formId}-reminder`;
  const noteSelectorLabelId = `${formId}-note-selector-label`;
  const noteSelectorId = `${formId}-note-selector`;

  const [title, setTitle] = useState(schedule?.title || '');
  const [description, setDescription] = useState(schedule?.description || '');
  const [allDay, setAllDay] = useState(schedule?.allDay || false);
  const [startTime, setStartTime] = useState(
    allDay ? formatDateLocal(schedule?.startTime) : formatDateTimeLocal(schedule?.startTime)
  );
  const [endTime, setEndTime] = useState(
    allDay ? formatDateLocal(schedule?.endTime) : formatDateTimeLocal(schedule?.endTime)
  );
  const [rrule, setRrule] = useState(schedule?.rrule || '');
  const [reminderMinutes, setReminderMinutes] = useState(schedule?.reminderMinutes || 0);
  const [selectedNoteIds, setSelectedNoteIds] = useState<string[]>(
    schedule?.notes.map(n => n.id) || []
  );
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [showNoteSelector, setShowNoteSelector] = useState(false);

  useEffect(() => {
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    getFocusableElements(panelRef.current)[0]?.focus();
    return () => previous?.focus();
  }, []);

  useEffect(() => {
    // 当切换全天模式时，转换时间格式
    if (allDay) {
      setStartTime(formatDateLocal(startTime));
      setEndTime(formatDateLocal(endTime));
    } else {
      if (startTime && !startTime.includes('T')) {
        setStartTime(startTime + 'T09:00');
      }
      if (endTime && !endTime.includes('T')) {
        setEndTime(endTime + 'T10:00');
      }
    }
  }, [allDay]);

  const handleSave = async () => {
    const currentTitle = (titleInputRef.current?.value ?? title).trim();
    const currentDescription = (descriptionInputRef.current?.value ?? description).trim();
    const currentStartTime = startTimeInputRef.current?.value ?? startTime;
    const currentEndTime = endTimeInputRef.current?.value ?? endTime;

    if (!currentTitle || !currentStartTime) {
      setError('请填写标题和开始时间');
      return;
    }

    setLoading(true);
    setError('');
    try {
      const payload: ScheduleCreateRequest = {
        title: currentTitle,
        description: currentDescription || undefined,
        startTime: allDay ? currentStartTime + 'T00:00:00' : currentStartTime + ':00',
        endTime: currentEndTime ? (allDay ? currentEndTime + 'T23:59:59' : currentEndTime + ':00') : undefined,
        allDay,
        rrule: rrule || undefined,
        reminderMinutes: reminderMinutes || undefined,
        noteIds: selectedNoteIds.length > 0 ? selectedNoteIds : undefined,
      };

      const result = schedule
        ? await updateSchedule(schedule.id, payload)
        : await createSchedule(payload);

      onSave(result);
    } catch (err) {
      setError((err as Error).message || '保存日程失败');
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    void handleSave();
  };

  const toggleNote = (noteId: string) => {
    setSelectedNoteIds(prev =>
      prev.includes(noteId)
        ? prev.filter(id => id !== noteId)
        : [...prev, noteId]
    );
  };

  const handlePanelKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Escape') {
      event.preventDefault();
      onClose();
      return;
    }

    if (event.key === 'Tab') {
      const focusable = getFocusableElements(panelRef.current);
      if (focusable.length === 0) return;

      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }
  };

  return (
    <div className="schedule-form-overlay" onClick={onClose}>
      <div
        className="schedule-form-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby={headingId}
        ref={panelRef}
        onClick={e => e.stopPropagation()}
        onKeyDown={handlePanelKeyDown}
      >
        <div className="schedule-form-header">
          <h2 id={headingId}>{schedule ? '编辑日程' : '创建日程'}</h2>
          <button className="schedule-form-close" onClick={onClose} aria-label="Close schedule form">✕</button>
        </div>

        <form onSubmit={handleSubmit} className="schedule-form-body">
          <div className="form-group">
            <label htmlFor={titleId}>标题</label>
            <input
              ref={titleInputRef}
              id={titleId}
              type="text"
              value={title}
              onChange={e => setTitle(e.target.value)}
              placeholder="日程标题"
              required
            />
          </div>

          <div className="form-group">
            <label htmlFor={descriptionId}>描述</label>
            <textarea
              ref={descriptionInputRef}
              id={descriptionId}
              value={description}
              onChange={e => setDescription(e.target.value)}
              placeholder="日程描述（可选）"
              rows={2}
            />
          </div>

          <div className="form-row">
            <label className="checkbox-label" htmlFor={allDayId}>
              <input
                id={allDayId}
                type="checkbox"
                checked={allDay}
                onChange={e => setAllDay(e.target.checked)}
              />
              全天
            </label>
          </div>

          <div className="form-row">
            <div className="form-group">
              <label htmlFor={startTimeId}>开始</label>
              <input
                ref={startTimeInputRef}
                id={startTimeId}
                type={allDay ? 'date' : 'datetime-local'}
                value={startTime}
                onChange={e => setStartTime(e.target.value)}
                required
              />
            </div>
            <div className="form-group">
              <label htmlFor={endTimeId}>结束</label>
              <input
                ref={endTimeInputRef}
                id={endTimeId}
                type={allDay ? 'date' : 'datetime-local'}
                value={endTime}
                onChange={e => setEndTime(e.target.value)}
              />
            </div>
          </div>

          <div className="form-row">
            <div className="form-group">
              <label htmlFor={rruleId}>重复</label>
              <select id={rruleId} value={rrule} onChange={e => setRrule(e.target.value)}>
                {REPEAT_OPTIONS.map(opt => (
                  <option key={opt.value} value={opt.value}>{opt.label}</option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label htmlFor={reminderId}>提醒</label>
              <select
                id={reminderId}
                value={reminderMinutes}
                onChange={e => setReminderMinutes(Number(e.target.value))}
              >
                {REMINDER_OPTIONS.map(opt => (
                  <option key={opt.value} value={opt.value}>{opt.label}</option>
                ))}
              </select>
            </div>
          </div>

          <div className="form-group">
            <label id={noteSelectorLabelId}>关联笔记</label>
            <button
              type="button"
              className="note-selector-toggle"
              aria-labelledby={noteSelectorLabelId}
              aria-expanded={showNoteSelector}
              aria-controls={noteSelectorId}
              onClick={() => setShowNoteSelector(!showNoteSelector)}
            >
              {selectedNoteIds.length > 0
                ? `已选择 ${selectedNoteIds.length} 篇笔记`
                : '点击选择笔记'}
              <span>{showNoteSelector ? '▲' : '▼'}</span>
            </button>
            {showNoteSelector && (
              <div id={noteSelectorId} className="note-selector-list">
                {notes.slice(0, 20).map(note => (
                  <label key={note.id} className="note-selector-item" htmlFor={`${formId}-note-${note.id}`}>
                    <input
                      id={`${formId}-note-${note.id}`}
                      type="checkbox"
                      checked={selectedNoteIds.includes(note.id)}
                      onChange={() => toggleNote(note.id)}
                    />
                    {note.title || '无标题'}
                  </label>
                ))}
              </div>
            )}
          </div>

          <div className="schedule-form-actions">
            {error && (
              <div className="schedule-form-error" role="alert">
                {error}
              </div>
            )}
            <button type="button" onClick={onClose} disabled={loading}>
              取消
            </button>
            <button
              type="button"
              className="primary"
              disabled={loading}
              onClick={() => { void handleSave(); }}
            >
              {loading ? '保存中...' : '保存'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
