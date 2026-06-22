import './TimelineView.css';
import type { Note, Schedule } from '../api';
import ScheduleCard from './ScheduleCard';

interface TimelineViewProps {
  notes: Note[];
  schedules: Schedule[];
  onSelectNote: (note: Note) => void;
  onSelectSchedule: (schedule: Schedule) => void;
  onScheduleStatusChange: (schedule: Schedule) => void;
  onClose: () => void;
}

type TimelineItem =
  | { type: 'note'; data: Note; displayTime: Date }
  | { type: 'schedule'; data: Schedule; displayTime: Date };

function parseDateTime(str: string): Date {
  // 支持 "2026-04-04 10:00:00" 和 "2026-04-04T10:00:00" 格式
  return new Date(str.replace(' ', 'T'));
}

function groupByDate(items: TimelineItem[]) {
  const groups: Record<string, TimelineItem[]> = {};
  [...items]
    .sort((a, b) => b.displayTime.getTime() - a.displayTime.getTime())
    .forEach(item => {
      const date = item.displayTime.toISOString().split('T')[0];
      if (!groups[date]) groups[date] = [];
      groups[date].push(item);
    });
  return groups;
}

function formatDate(dateStr: string) {
  const date = new Date(dateStr);
  const today = new Date();
  const yesterday = new Date(today);
  yesterday.setDate(today.getDate() - 1);

  const d = date.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' });
  const todayStr = today.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' });
  const yestStr = yesterday.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' });

  if (d === todayStr) return `今天 · ${d}`;
  if (d === yestStr) return `昨天 · ${d}`;
  return d;
}

export default function TimelineView({
  notes,
  schedules,
  onSelectNote,
  onSelectSchedule,
  onScheduleStatusChange,
  onClose
}: TimelineViewProps) {
  // 合并笔记和日程为统一的时间线项
  const items: TimelineItem[] = [
    ...notes.map(note => ({
      type: 'note' as const,
      data: note,
      displayTime: parseDateTime(note.updatedAt)
    })),
    ...schedules.map(schedule => ({
      type: 'schedule' as const,
      data: schedule,
      displayTime: parseDateTime(schedule.startTime)
    }))
  ];

  const groups = groupByDate(items);
  const dates = Object.keys(groups).sort((a, b) => b.localeCompare(a));

  return (
    <div className="timeline-overlay">
      <div className="timeline-panel">
        <div className="timeline-header">
          <h2>时间线</h2>
          <span className="timeline-stats">{notes.length} 篇笔记 · {schedules.length} 个日程</span>
          <button type="button" className="timeline-close" aria-label="Close timeline" onClick={onClose}>✕</button>
        </div>
        <div className="timeline-body">
          {dates.length === 0 ? (
            <div className="timeline-empty">暂无内容</div>
          ) : (
            dates.map(date => (
              <div key={date} className="timeline-group">
                <div className="timeline-date-label">
                  <span className="timeline-dot" />
                  {formatDate(date)}
                </div>
                <div className="timeline-items">
                  {groups[date].map(item => (
                    item.type === 'note' ? (
                      <button
                        key={`note-${item.data.id}`}
                        type="button"
                        className="timeline-note"
                        aria-label={`Open timeline note ${item.data.title || 'Untitled'}`}
                        onClick={() => { onSelectNote(item.data); onClose(); }}
                      >
                        <div className="timeline-note-title">{item.data.title || '无标题'}</div>
                        <div className="timeline-note-preview">
                          {item.data.content.replace(/[#*`>\-\[\]]/g, '').substring(0, 80)}
                        </div>
                        {item.data.tags && item.data.tags.length > 0 && (
                          <div className="timeline-note-tags">
                            {item.data.tags.map(tag => (
                              <span key={tag.id} className="timeline-tag">{tag.name}</span>
                            ))}
                          </div>
                        )}
                        <div className="timeline-note-time">
                          {item.data.updatedAt.split(' ')[1]?.substring(0, 5) ||
                           item.data.updatedAt.split('T')[1]?.substring(0, 5)}
                        </div>
                      </button>
                    ) : (
                      <ScheduleCard
                        key={`schedule-${item.data.id}`}
                        schedule={item.data}
                        onSelect={onSelectSchedule}
                        onStatusChange={onScheduleStatusChange}
                      />
                    )
                  ))}
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}
