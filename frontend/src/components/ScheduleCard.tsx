import { useState } from 'react';
import type { Schedule } from '../api';
import { updateScheduleStatus } from '../api';
import './ScheduleCard.css';

interface ScheduleCardProps {
  schedule: Schedule;
  onSelect: (schedule: Schedule) => void;
  onStatusChange: (schedule: Schedule) => void;
}

export default function ScheduleCard({ schedule, onSelect, onStatusChange }: ScheduleCardProps) {
  const [loading, setLoading] = useState(false);

  const handleComplete = async (e: React.MouseEvent) => {
    e.stopPropagation();
    if (loading || schedule.status === 'completed') return;
    setLoading(true);
    try {
      const updated = await updateScheduleStatus(schedule.id, 'completed');
      onStatusChange(updated);
    } finally {
      setLoading(false);
    }
  };

  const formatTime = (time: string) => {
    return new Date(time).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  };

  const statusLabel = {
    pending: '待办',
    in_progress: '进行中',
    completed: '已完成',
    expired: '已过期'
  }[schedule.status];

  const rruleLabel = (rrule?: string) => {
    if (!rrule) return null;
    if (rrule.includes('DAILY')) return '每天';
    if (rrule.includes('WEEKLY')) return '每周';
    if (rrule.includes('MONTHLY')) return '每月';
    if (rrule.includes('YEARLY')) return '每年';
    return '重复';
  };

  return (
    <div className={`schedule-card schedule-${schedule.status}`} onClick={() => onSelect(schedule)}>
      <div className="schedule-checkbox" onClick={handleComplete}>
        {schedule.status === 'completed' ? '✓' : '○'}
      </div>
      <div className="schedule-content">
        <div className="schedule-title">{schedule.title}</div>
        <div className="schedule-meta">
          {schedule.allDay ? '全天' : `${formatTime(schedule.startTime)}${schedule.endTime ? ' - ' + formatTime(schedule.endTime) : ''}`}
          {schedule.rrule && <span className="schedule-repeat">{rruleLabel(schedule.rrule)}</span>}
          <span className={`schedule-status status-${schedule.status}`}>{statusLabel}</span>
          {schedule.notes.length > 0 && <span className="schedule-notes-count">关联 {schedule.notes.length} 篇笔记</span>}
        </div>
      </div>
    </div>
  );
}
