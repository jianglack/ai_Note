import { useState, useMemo, useRef, type CSSProperties, type KeyboardEvent } from 'react';
import { FixedSizeList, type LegacyListChildComponentProps } from './VirtualList';
import { PlusIcon } from '@heroicons/react/24/outline';
import { useUiStore } from '../stores/uiStore';
import { useNoteStore } from '../stores/noteStore';
import NoteListBatchToolbar from './NoteListBatchToolbar';
import WorkflowCheckbox from './workflow/WorkflowCheckbox';
import { useMeasuredHeight } from '../hooks/useMeasuredHeight';
import type { Note } from '../api';
import './NoteListPanel.css';

type SortMode = 'recent' | 'oldest' | 'ai';
const NOTE_ROW_HEIGHT = 132;

interface NoteListPanelProps {
  notes: Note[];
  selectedNoteId?: string | null;
  onSelectNote: (note: Note) => void;
  onCreateNote: () => void;
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr);
  const now = new Date();
  const today = now.toISOString().slice(0, 10);
  const yesterday = new Date(now.getTime() - 86400000).toISOString().slice(0, 10);
  const d = dateStr.slice(0, 10);

  if (d === today) {
    return `今天 ${date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}`;
  }
  if (d === yesterday) {
    return `昨天 ${date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}`;
  }
  return `${date.getMonth() + 1}月 ${date.getDate()}日`;
}

function stripHtml(html: string): string {
  return html.replace(/<[^>]*>/g, '').replace(/\s+/g, ' ').trim();
}

type NoteListItemData = {
  notes: Note[];
  previews: Record<string, string>;
};

export default function NoteListPanel({
  notes,
  selectedNoteId,
  onSelectNote,
  onCreateNote,
}: NoteListPanelProps) {
  const ui = useUiStore();
  const { folders } = useNoteStore();
  const [sortMode, setSortMode] = useState<SortMode>('recent');
  const listBodyRef = useRef<HTMLDivElement>(null);
  const listHeight = useMeasuredHeight(listBodyRef, 480);

  const todayStr = new Date().toISOString().slice(0, 10);

  // Filter notes based on sidebar filter and selected folder
  const filtered = useMemo(() => {
    let result = notes;

    // Apply sidebar filter
    switch (ui.sidebarFilter) {
      case 'today':
        result = result.filter(n => n.updatedAt?.startsWith(todayStr));
        break;
      case 'starred':
        result = result.filter(n => n.starred);
        break;
      case 'ai':
        result = [];
        break;
    }

    // Apply folder filter
    if (ui.selectedFolderId) {
      if (ui.selectedFolderId === '__unfiled__') {
        result = result.filter(n => !n.folderId);
      } else {
        result = result.filter(n => n.folderId === ui.selectedFolderId);
      }
    }

    return result;
  }, [notes, ui.sidebarFilter, ui.selectedFolderId, todayStr]);

  // Determine panel title
  const folderName = useMemo(() => {
    if (ui.selectedFolderId === '__unfiled__') return '未分类';
    if (ui.selectedFolderId) {
      const f = folders.find(f => f.id === ui.selectedFolderId);
      return f?.name || '全部笔记';
    }
    switch (ui.sidebarFilter) {
      case 'today': return '今天';
      case 'starred': return '收藏';
      case 'ai': return 'AI 整理中';
      default: return '全部笔记';
    }
  }, [ui.selectedFolderId, ui.sidebarFilter, folders]);

  const sorted = useMemo(() => {
    const list = [...filtered];
    // Pinned notes always first
    const pinned = list.filter(n => n.pinned);
    const unpinned = list.filter(n => !n.pinned);

    const sortFn = (a: Note, b: Note) => {
      if (sortMode === 'oldest') {
        return new Date(a.updatedAt).getTime() - new Date(b.updatedAt).getTime();
      }
      // 'recent' and 'ai' both sort by most recent
      return new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime();
    };

    pinned.sort(sortFn);
    unpinned.sort(sortFn);
    return [...pinned, ...unpinned];
  }, [filtered, sortMode]);

  const previewById = useMemo(() => {
    return Object.fromEntries(sorted.map((note) => [
      note.id,
      stripHtml(note.content).slice(0, 80),
    ]));
  }, [sorted]);

  const activateNote = (note: Note) => {
    if (ui.multiSelectMode) {
      ui.toggleMultiNoteSelection(note.id);
    } else {
      onSelectNote(note);
    }
  };

  const focusAdjacentNote = (current: HTMLElement, direction: 1 | -1) => {
    const options = Array.from(
      current.parentElement?.querySelectorAll<HTMLElement>('[role="option"]') ?? []
    );
    const currentIndex = options.indexOf(current);
    const next = options[currentIndex + direction];
    next?.focus();
  };

  const handleNoteKeyDown = (event: KeyboardEvent<HTMLDivElement>, note: Note) => {
    if (event.target !== event.currentTarget) {
      return;
    }

    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      activateNote(note);
      return;
    }

    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      focusAdjacentNote(event.currentTarget, event.key === 'ArrowDown' ? 1 : -1);
    }
  };

  const renderNoteRow = ({ index, style, data }: LegacyListChildComponentProps<NoteListItemData>) => {
    const note = data.notes[index];
    const active = note.id === selectedNoteId;
    const isSelected = ui.multiSelectedNoteIds.has(note.id);
    const optionSelected = ui.multiSelectMode ? isSelected : active;
    const preview = data.previews[note.id];
    const rowStyle: CSSProperties = {
      ...style,
      boxSizing: 'border-box',
    };

    return (
      <div
        key={note.id}
        className={`note-list-item ${active ? 'active' : ''} ${isSelected ? 'multi-selected' : ''}`}
        role="option"
        aria-selected={optionSelected}
        tabIndex={active || (!selectedNoteId && index === 0) ? 0 : -1}
        onClick={() => activateNote(note)}
        onKeyDown={(event) => handleNoteKeyDown(event, note)}
        style={rowStyle}
      >
        <div className="note-list-item-top">
          {ui.multiSelectMode && (
            <span onClick={(event) => event.stopPropagation()}>
              <WorkflowCheckbox
                checked={isSelected}
                onChange={() => ui.toggleMultiNoteSelection(note.id)}
              />
            </span>
          )}
          <h3 className="note-list-item-title">
            {note.pinned && <span className="note-list-pin">📌</span>}
            {note.title || 'Untitled'}
          </h3>
          <span className="note-list-item-date">{formatDate(note.updatedAt)}</span>
        </div>
        {preview && (
          <p className="note-list-item-preview">{preview}</p>
        )}
        {note.tags && note.tags.length > 0 && (
          <div className="note-list-item-tags">
            {note.tags.map((tag) => (
              <span key={tag.id} className={`note-list-tag ${active ? 'active' : ''}`}>
                {tag.name}
              </span>
            ))}
          </div>
        )}
      </div>
    );
  };

  return (
    <section className="note-list-panel" style={{ position: 'relative' }}>
      {/* Header */}
      <div className="note-list-header">
        <div className="note-list-header-top">
          <h2 className="note-list-title">{folderName}</h2>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span className="note-list-count">{filtered.length} 篇</span>
            <button
              onClick={() => ui.toggleMultiSelect()}
              style={{
                fontSize: 10,
                padding: '2px 8px',
                borderRadius: 10,
                cursor: 'pointer',
                fontFamily: 'inherit',
                transition: 'all 0.12s',
                border: ui.multiSelectMode
                  ? '1px solid var(--color-accent, #b8452e)'
                  : '1px solid var(--color-border, #e0d4b8)',
                background: ui.multiSelectMode
                  ? 'var(--color-accent, #b8452e)'
                  : 'transparent',
                color: ui.multiSelectMode
                  ? 'var(--color-paper-0, #fbf7ee)'
                  : 'var(--color-text-secondary, #9a8b73)',
              }}
              title={ui.multiSelectMode ? '退出多选' : '多选模式'}
            >
              {ui.multiSelectMode ? '退出多选' : '多选'}
            </button>
          </div>
        </div>
        <div className="note-list-filters">
          {([
            { id: 'recent', label: '最近' },
            { id: 'oldest', label: '时间倒序' },
            { id: 'ai', label: 'AI 相关' },
          ] as { id: SortMode; label: string }[]).map((f) => (
            <button
              key={f.id}
              className={`note-list-filter ${sortMode === f.id ? 'active' : ''}`}
              onClick={() => setSortMode(f.id)}
            >
              {f.label}
            </button>
          ))}
        </div>
      </div>

      {/* Multi-select header bar */}
      {ui.multiSelectMode && sorted.length > 0 && (
        <div style={{
          display: 'flex', alignItems: 'center', gap: 8,
          padding: '10px 12px',
          borderBottom: '1px solid rgba(74,64,50,0.12)',
        }}>
          <WorkflowCheckbox
            checked={sorted.length > 0 && sorted.every(n => ui.multiSelectedNoteIds.has(n.id))}
            indeterminate={ui.multiSelectedNoteIds.size > 0 && !sorted.every(n => ui.multiSelectedNoteIds.has(n.id))}
            onChange={() => {
              if (sorted.every(n => ui.multiSelectedNoteIds.has(n.id))) {
                ui.clearMultiSelection();
              } else {
                ui.selectAllMultiNotes(sorted.map(n => n.id));
              }
            }}
          />
          <span style={{ fontSize: 13, color: 'var(--color-accent, #b8452e)', fontWeight: 500 }}>
            多选模式
          </span>
          <span style={{ flex: 1, textAlign: 'right', fontSize: 12, color: 'var(--color-text-secondary, #7a6b58)' }}>
            已选 {ui.multiSelectedNoteIds.size} 篇
          </span>
          <span
            style={{ fontSize: 13, color: 'var(--color-text-secondary, #7a6b58)', cursor: 'pointer' }}
            onClick={() => ui.setMultiSelectMode(false)}
          >
            退出
          </span>
        </div>
      )}

      {/* List */}
      <div
        ref={listBodyRef}
        className="note-list-body"
        role="listbox"
        aria-label="Notes"
        aria-multiselectable={ui.multiSelectMode || undefined}
      >
        {sorted.length === 0 && (
          <div className="note-list-empty">暂无笔记</div>
        )}
        {sorted.length > 0 && (
          <FixedSizeList<NoteListItemData>
            height={listHeight}
            width="100%"
            itemCount={sorted.length}
            itemSize={NOTE_ROW_HEIGHT}
            itemData={{ notes: sorted, previews: previewById }}
            itemKey={(index, data) => data.notes[index].id}
          >
            {renderNoteRow}
          </FixedSizeList>
        )}
      </div>

      {/* Create button (always shown) */}
      {!ui.multiSelectMode && (
        <div className="note-list-footer">
          <button className="note-list-create-btn" onClick={onCreateNote}>
            <PlusIcon className="w-4 h-4" />
            新建笔记
            <span className="note-list-create-hint">⌘N</span>
          </button>
        </div>
      )}

      {/* Floating batch toolbar */}
      {ui.multiSelectMode && ui.multiSelectedNoteIds.size > 0 && (
        <NoteListBatchToolbar />
      )}
    </section>
  );
}
