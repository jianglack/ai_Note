import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { VariableSizeList, type ListChildComponentProps } from 'react-window';
import './TimelineView.css';
import { updateScheduleStatus, type Note, type Schedule } from '../api';
import { useMeasuredHeight } from '../hooks/useMeasuredHeight';
import {
  ToolControlRail,
  ToolDetailPanel,
  ToolWorkbenchShell,
} from './workbench';

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
type TimelineRow =
  | { kind: 'date'; date: string }
  | { kind: 'item'; item: TimelineItem };

const scheduleStatusLabel: Record<Schedule['status'], string> = {
  pending: '待办',
  in_progress: '进行中',
  completed: '已完成',
  expired: '已过期',
};

function parseDateTime(str: string): Date {
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

function formatTime(date: Date) {
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
}

function formatScheduleTime(schedule: Schedule) {
  if (schedule.allDay) {
    return '全天';
  }
  const start = formatTime(parseDateTime(schedule.startTime));
  const end = schedule.endTime ? ` - ${formatTime(parseDateTime(schedule.endTime))}` : '';
  return `${start}${end}`;
}

function getItemId(item: TimelineItem) {
  return `${item.type}-${item.data.id}`;
}

export default function TimelineView({
  notes,
  schedules,
  onSelectNote,
  onSelectSchedule,
  onScheduleStatusChange,
  onClose
}: TimelineViewProps) {
  const bodyRef = useRef<HTMLDivElement>(null);
  const listRef = useRef<VariableSizeList<TimelineRow[]>>(null);
  const bodyHeight = useMeasuredHeight(bodyRef, 640);
  const [selectedItem, setSelectedItem] = useState<TimelineItem | null>(null);
  const [statusUpdating, setStatusUpdating] = useState(false);

  const items = useMemo<TimelineItem[]>(() => [
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
  ], [notes, schedules]);

  const groups = useMemo(() => groupByDate(items), [items]);
  const dates = useMemo(() => Object.keys(groups).sort((a, b) => b.localeCompare(a)), [groups]);
  const notePreviewById = useMemo(() => {
    return Object.fromEntries(notes.map((note) => [
      note.id,
      note.content.replace(/[#*`>\-\[\]]/g, '').replace(/\s+/g, ' ').trim().substring(0, 80),
    ]));
  }, [notes]);
  const rows = useMemo<TimelineRow[]>(() => {
    return dates.flatMap((date) => [
      { kind: 'date' as const, date },
      ...groups[date].map((item) => ({ kind: 'item' as const, item })),
    ]);
  }, [dates, groups]);
  const activeScheduleCount = useMemo(() => (
    schedules.filter((schedule) => schedule.status !== 'completed').length
  ), [schedules]);

  useEffect(() => {
    listRef.current?.resetAfterIndex(0, true);
  }, [rows]);

  useEffect(() => {
    if (!selectedItem) {
      return;
    }

    const currentItem = items.find((item) => getItemId(item) === getItemId(selectedItem));
    if (!currentItem) {
      setSelectedItem(null);
      return;
    }

    if (currentItem !== selectedItem) {
      setSelectedItem(currentItem);
    }
  }, [items, selectedItem]);

  const getRowHeight = useCallback((index: number) => {
    const row = rows[index];
    if (!row || row.kind === 'date') return 42;
    return 132;
  }, [rows]);

  const openSelectedItem = useCallback(() => {
    if (!selectedItem) {
      return;
    }

    if (selectedItem.type === 'note') {
      onSelectNote(selectedItem.data);
      onClose();
      return;
    }

    onSelectSchedule(selectedItem.data);
  }, [onClose, onSelectNote, onSelectSchedule, selectedItem]);

  const completeSelectedSchedule = useCallback(async () => {
    if (!selectedItem || selectedItem.type !== 'schedule' || selectedItem.data.status === 'completed' || statusUpdating) {
      return;
    }

    setStatusUpdating(true);
    try {
      const updated = await updateScheduleStatus(selectedItem.data.id, 'completed');
      onScheduleStatusChange(updated);
      setSelectedItem({
        type: 'schedule',
        data: updated,
        displayTime: parseDateTime(updated.startTime),
      });
    } finally {
      setStatusUpdating(false);
    }
  }, [onScheduleStatusChange, selectedItem, statusUpdating]);

  const renderTimelineRow = ({ index, style, data }: ListChildComponentProps<TimelineRow[]>) => {
    const row = data[index];
    const rowStyle: CSSProperties = { ...style, boxSizing: 'border-box' };

    if (row.kind === 'date') {
      return (
        <div className="timeline-date-label timeline-virtual-date" style={rowStyle}>
          <span className="timeline-dot" />
          {formatDate(row.date)}
        </div>
      );
    }

    const item = row.item;
    const isSelected = selectedItem ? getItemId(selectedItem) === getItemId(item) : false;

    return (
      <div className="timeline-virtual-item" style={rowStyle}>
        {item.type === 'note' ? (
          <button
            key={`note-${item.data.id}`}
            type="button"
            className={`timeline-note ${isSelected ? 'timeline-item-selected' : ''}`}
            aria-label={`Select timeline note ${item.data.title || 'Untitled'}`}
            onClick={() => setSelectedItem(item)}
          >
            <div className="timeline-note-title">{item.data.title || 'Untitled'}</div>
            <div className="timeline-note-preview">
              {notePreviewById[item.data.id]}
            </div>
            {item.data.tags && item.data.tags.length > 0 && (
              <div className="timeline-note-tags">
                {item.data.tags.map(tag => (
                  <span key={tag.id} className="timeline-tag">{tag.name}</span>
                ))}
              </div>
            )}
            <div className="timeline-note-time">{formatTime(item.displayTime)}</div>
          </button>
        ) : (
          <button
            key={`schedule-${item.data.id}`}
            type="button"
            className={`timeline-schedule timeline-schedule-${item.data.status} ${isSelected ? 'timeline-item-selected' : ''}`}
            aria-label={`Select timeline schedule ${item.data.title || 'Untitled'}`}
            onClick={() => setSelectedItem(item)}
          >
            <span className="timeline-schedule-marker" aria-hidden="true">
              {item.data.status === 'completed' ? '✓' : '○'}
            </span>
            <span className="timeline-schedule-content">
              <span className="timeline-schedule-title">{item.data.title}</span>
              <span className="timeline-schedule-meta">
                {formatScheduleTime(item.data)}
                <span className={`timeline-status status-${item.data.status}`}>{scheduleStatusLabel[item.data.status]}</span>
                {item.data.notes.length > 0 && <span>关联 {item.data.notes.length} 篇笔记</span>}
              </span>
            </span>
          </button>
        )}
      </div>
    );
  };

  let detailActions: ReactNode | undefined;
  if (selectedItem?.type === 'note') {
    detailActions = (
      <button type="button" className="twb-action twb-action-primary" onClick={openSelectedItem}>
        打开笔记
      </button>
    );
  } else if (selectedItem?.type === 'schedule') {
    detailActions = (
      <>
        <button type="button" className="twb-action twb-action-primary" onClick={openSelectedItem}>
          编辑日程
        </button>
        {selectedItem.data.status !== 'completed' && (
          <button
            type="button"
            className="twb-action twb-action-secondary"
            disabled={statusUpdating}
            onClick={completeSelectedSchedule}
          >
            {statusUpdating ? '更新中' : '标记完成'}
          </button>
        )}
      </>
    );
  }

  return (
    <ToolWorkbenchShell
      title="时间线"
      subtitle={`${notes.length} 篇笔记 · ${schedules.length} 个日程`}
      onBack={onClose}
      backLabel="关闭时间线"
      closeOnEscape
      mainClassName="timeline-workbench-main"
      leftRail={(
        <ToolControlRail
          sections={[
            {
              key: 'counts',
              title: '类型',
              content: (
                <div className="timeline-counts">
                  <span><strong>{notes.length}</strong> 笔记</span>
                  <span><strong>{schedules.length}</strong> 日程</span>
                  <span><strong>{activeScheduleCount}</strong> 未完成</span>
                </div>
              ),
            },
            {
              key: 'range',
              title: '排序',
              content: (
                <div className="timeline-help">
                  <p>笔记按更新时间排序。</p>
                  <p>日程按开始时间排序。</p>
                </div>
              ),
            },
          ]}
        />
      )}
      detailPanel={(
        <ToolDetailPanel
          title="时间线详情"
          subtitle={selectedItem?.type === 'note' ? '笔记' : selectedItem?.type === 'schedule' ? '日程' : undefined}
          empty={!selectedItem}
          emptyMessage="选择一个时间线项目查看详情。"
          actions={detailActions}
        >
          {selectedItem?.type === 'note' && (
            <div className="timeline-detail">
              <div className="timeline-detail-title">{selectedItem.data.title || 'Untitled'}</div>
              <div className="timeline-detail-meta">更新时间：{formatDate(selectedItem.displayTime.toISOString().split('T')[0])} {formatTime(selectedItem.displayTime)}</div>
              <p>{notePreviewById[selectedItem.data.id] || '暂无预览'}</p>
              {selectedItem.data.tags.length > 0 && (
                <div className="timeline-note-tags">
                  {selectedItem.data.tags.map(tag => (
                    <span key={tag.id} className="timeline-tag">{tag.name}</span>
                  ))}
                </div>
              )}
            </div>
          )}
          {selectedItem?.type === 'schedule' && (
            <div className="timeline-detail">
              <div className="timeline-detail-title">{selectedItem.data.title}</div>
              <div className="timeline-detail-meta">时间：{formatScheduleTime(selectedItem.data)}</div>
              <div className="timeline-detail-meta">状态：{scheduleStatusLabel[selectedItem.data.status]}</div>
              <div className="timeline-detail-meta">关联笔记：{selectedItem.data.notes.length} 篇</div>
            </div>
          )}
        </ToolDetailPanel>
      )}
    >
      <div className="timeline-body" ref={bodyRef}>
        {dates.length === 0 ? (
          <div className="timeline-empty">暂无内容</div>
        ) : (
          <VariableSizeList<TimelineRow[]>
            ref={listRef}
            className="timeline-virtual-list"
            height={bodyHeight}
            width="100%"
            itemCount={rows.length}
            itemData={rows}
            itemSize={getRowHeight}
            itemKey={(index, data) => {
              const row = data[index];
              if (row.kind === 'date') return `date-${row.date}`;
              return getItemId(row.item);
            }}
          >
            {renderTimelineRow}
          </VariableSizeList>
        )}
      </div>
    </ToolWorkbenchShell>
  );
}
