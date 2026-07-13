import { useMemo, useState, useEffect, useCallback, useRef, type CSSProperties, type KeyboardEvent, type MouseEvent, type ReactNode } from 'react';
import { FixedSizeList } from './VirtualList';
import {
  GlobeAltIcon,
  ClockIcon,
  CalendarDaysIcon,
  ChartBarIcon,
  MagnifyingGlassIcon,
  XMarkIcon,
  Cog6ToothIcon,
  EllipsisHorizontalIcon,
  PlusIcon,
} from '@heroicons/react/24/outline';
import { useAuthStore } from '../stores/authStore';
import { useUiStore } from '../stores/uiStore';
import type { SidebarFilter } from '../stores/uiStore';
import { useNoteStore } from '../stores/noteStore';
import { useScheduleStore } from '../stores/scheduleStore';
import { useNotes } from '../hooks/useNotes';
import { useMeasuredHeight } from '../hooks/useMeasuredHeight';

const FOLDER_COLORS = [
  { name: '朱砂', value: '#b8452e' },
  { name: '青苔', value: '#6b7a5a' },
  { name: '琥珀', value: '#c9a959' },
  { name: '紫藤', value: '#8a6bb1' },
  { name: '靛蓝', value: '#4a7ba6' },
  { name: '墨色', value: '#9a8b73' },
];
const SEARCH_RESULT_ROW_HEIGHT = 112;

function stripHtmlPreview(content: string, limit: number) {
  return content.replace(/<[^>]*>/g, '').replace(/\s+/g, ' ').trim().slice(0, limit);
}

interface SidebarProps {
  onLogout: () => void;
}

interface SidebarRowButtonProps {
  children: ReactNode;
  onClick: () => void;
  active?: boolean;
  className?: string;
  ariaLabel: string;
  style?: CSSProperties;
  onMouseEnter?: (event: MouseEvent<HTMLButtonElement>) => void;
  onMouseLeave?: (event: MouseEvent<HTMLButtonElement>) => void;
}

function SidebarRowButton({
  children,
  onClick,
  active,
  className,
  ariaLabel,
  style,
  onMouseEnter,
  onMouseLeave,
}: SidebarRowButtonProps) {
  return (
    <button
      type="button"
      aria-label={ariaLabel}
      aria-current={active ? 'page' : undefined}
      className={className}
      onClick={onClick}
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
      style={{
        border: 'none',
        background: 'transparent',
        fontFamily: 'inherit',
        textAlign: 'left',
        width: '100%',
        ...style,
      }}
    >
      {children}
    </button>
  );
}

interface SidebarIconButtonProps {
  children: ReactNode;
  onClick: () => void;
  'aria-label': string;
  title?: string;
  className?: string;
  style?: CSSProperties;
  onMouseEnter?: (event: MouseEvent<HTMLButtonElement>) => void;
  onMouseLeave?: (event: MouseEvent<HTMLButtonElement>) => void;
}

function SidebarIconButton({
  children,
  onClick,
  'aria-label': ariaLabel,
  title,
  className,
  style,
  onMouseEnter,
  onMouseLeave,
}: SidebarIconButtonProps) {
  return (
    <button
      type="button"
      aria-label={ariaLabel}
      title={title}
      className={className}
      onClick={onClick}
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
      style={{
        border: 'none',
        background: 'transparent',
        padding: 0,
        cursor: 'pointer',
        fontFamily: 'inherit',
        color: 'inherit',
        ...style,
      }}
    >
      {children}
    </button>
  );
}

function SidebarToolButton({
  children,
  onClick,
  ariaLabel,
}: {
  children: ReactNode;
  onClick: () => void;
  ariaLabel: string;
}) {
  return (
    <SidebarRowButton
      ariaLabel={ariaLabel}
      onClick={onClick}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        padding: '5px 10px',
        borderRadius: 6,
        cursor: 'pointer',
        fontSize: 12,
        color: 'var(--color-text-secondary, #7a6b58)',
        transition: 'background .12s',
      }}
      onMouseEnter={e => e.currentTarget.style.background = 'rgba(74,64,50,0.06)'}
      onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
    >
      {children}
    </SidebarRowButton>
  );
}

export default function Sidebar({ onLogout }: SidebarProps) {
  const { user } = useAuthStore();
  const ui = useUiStore();
  const { notes, folders, selectedNote } = useNoteStore();
  const scheduleStore = useScheduleStore();
  const {
    handleSearch, handleOpenTrash, handleCreateFolder, handleSelectNote,
    handleDeleteFolder, handleRenameFolder, handleUpdateFolderColor,
  } = useNotes();

  // Context menu state
  const [ctxMenu, setCtxMenu] = useState<{ x: number; y: number; folderId: string } | null>(null);
  const [renaming, setRenaming] = useState<{ id: string; name: string } | null>(null);
  const folderMenuRef = useRef<HTMLDivElement | null>(null);
  const renameDialogRef = useRef<HTMLDivElement | null>(null);
  const renamePreviousFocusRef = useRef<HTMLElement | null>(null);
  const searchResultsRef = useRef<HTMLDivElement | null>(null);
  const searchResultsHeight = useMeasuredHeight(searchResultsRef, 420);
  const isRenameDialogOpen = Boolean(renaming);

  // Close menu on click outside
  useEffect(() => {
    if (!ctxMenu) return;
    const close = () => setCtxMenu(null);
    window.addEventListener('click', close);
    return () => window.removeEventListener('click', close);
  }, [ctxMenu]);

  useEffect(() => {
    if (!ctxMenu) return;
    window.setTimeout(() => {
      folderMenuRef.current?.querySelector<HTMLElement>('[role="menuitem"]')?.focus();
    }, 0);
  }, [ctxMenu]);

  useEffect(() => {
    if (!isRenameDialogOpen) return;
    renamePreviousFocusRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    window.setTimeout(() => renameDialogRef.current?.focus(), 0);

    return () => {
      renamePreviousFocusRef.current?.focus();
      renamePreviousFocusRef.current = null;
    };
  }, [isRenameDialogOpen]);

  const handleFolderMore = useCallback((e: MouseEvent<HTMLElement>, folderId: string) => {
    e.stopPropagation();
    const rect = e.currentTarget.getBoundingClientRect();
    setCtxMenu({ x: rect.left, y: rect.bottom + 4, folderId });
  }, []);

  const handleFolderMenuKeyDown = useCallback((event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Escape') {
      event.preventDefault();
      setCtxMenu(null);
      return;
    }

    if (
      event.key !== 'ArrowDown' &&
      event.key !== 'ArrowUp' &&
      event.key !== 'Home' &&
      event.key !== 'End'
    ) {
      return;
    }

    const items = Array.from(event.currentTarget.querySelectorAll<HTMLElement>('[role="menuitem"]'))
      .filter(element => element.offsetParent !== null || element === document.activeElement);

    if (items.length === 0) {
      event.preventDefault();
      return;
    }

    const currentIndex = items.indexOf(document.activeElement as HTMLElement);
    let nextIndex = 0;

    if (event.key === 'ArrowDown') {
      nextIndex = currentIndex >= 0 ? (currentIndex + 1) % items.length : 0;
    } else if (event.key === 'ArrowUp') {
      nextIndex = currentIndex >= 0 ? (currentIndex - 1 + items.length) % items.length : items.length - 1;
    } else if (event.key === 'End') {
      nextIndex = items.length - 1;
    }

    event.preventDefault();
    items[nextIndex]?.focus();
  }, []);

  const closeRenameDialog = useCallback(() => {
    setRenaming(null);
  }, []);

  const confirmRenameFolder = useCallback(() => {
    if (!renaming?.name.trim()) return;
    handleRenameFolder(renaming.id, renaming.name.trim());
    setRenaming(null);
  }, [handleRenameFolder, renaming]);

  const handleRenameDialogKeyDown = useCallback((event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Escape') {
      event.preventDefault();
      closeRenameDialog();
      return;
    }

    if (event.key !== 'Tab') return;

    const focusable = Array.from(event.currentTarget.querySelectorAll<HTMLElement>(
      'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])'
    )).filter(element => element.offsetParent !== null || element === document.activeElement);

    if (focusable.length === 0) {
      event.preventDefault();
      return;
    }

    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    const active = document.activeElement;

    if (event.shiftKey && active === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && active === last) {
      event.preventDefault();
      first.focus();
    } else if (!event.currentTarget.contains(active)) {
      event.preventDefault();
      first.focus();
    }
  }, [closeRenameDialog]);

  const todayStr = new Date().toISOString().slice(0, 10);
  const shortcuts: { id: SidebarFilter; label: string; count: number; icon: string; accent?: boolean }[] = [
    { id: 'today', label: '今天', count: notes.filter(n => n.updatedAt?.startsWith(todayStr)).length, icon: '◔' },
    { id: 'all', label: '全部笔记', count: notes.length, icon: '≡' },
    { id: 'starred', label: '收藏', count: notes.filter(n => n.starred).length, icon: '☆' },
    { id: 'ai', label: 'AI 整理中', count: 0, icon: '✦', accent: true },
  ];

  // Collect all unique tags from notes
  const allTags = useMemo(() => {
    const tagSet = new Set<string>();
    notes.forEach(n => n.tags?.forEach(t => tagSet.add(t.name)));
    return Array.from(tagSet).slice(0, 8);
  }, [notes]);

  const searchPreviewById = useMemo(() => {
    return Object.fromEntries(ui.searchResults.map((note) => [
      note.id,
      stripHtmlPreview(note.content, 60),
    ]));
  }, [ui.searchResults]);

  const renderSearchResult = ({ index, style }: { index: number; style: CSSProperties }) => {
    const note = ui.searchResults[index];
    const active = selectedNote?.id === note.id;

    return (
      <SidebarRowButton
        key={note.id}
        ariaLabel={`Open note ${note.title || 'Untitled'}`}
        active={active}
        style={{
          ...style,
          boxSizing: 'border-box',
          padding: '10px 18px',
          borderBottom: '1px solid var(--color-border, #e0d4b8)',
          cursor: 'pointer',
          background: active ? 'rgba(184,69,46,0.08)' : 'transparent',
          borderLeft: active ? '3px solid var(--color-accent, #b8452e)' : '3px solid transparent',
          transition: 'all .12s',
        }}
        onClick={() => {
          handleSelectNote(note);
          ui.clearSearch();
        }}
      >
        <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 3, color: 'var(--color-text, #2b2620)' }}>{note.title || 'Untitled'}</div>
        <div style={{ fontSize: 11, color: 'var(--color-text-secondary, #7a6b58)', marginBottom: 4, lineHeight: 1.5 }}>
          {searchPreviewById[note.id]}...
        </div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
          {note.tags?.map((tag) => (
            <span key={tag.id} style={{
              fontSize: 10, padding: '1px 6px',
              background: 'var(--color-paper-2, #f5efe0)',
              borderRadius: 3, color: 'var(--color-text-secondary, #7a6b58)',
            }}>{tag.name}</span>
          ))}
        </div>
      </SidebarRowButton>
    );
  };

  // User initial for avatar
  const userInitial = user?.username?.charAt(0) || '用';

  return (
    <aside className="sidebar flex flex-col border-r border-border bg-sidebar overflow-hidden" style={{ gridRow: '1 / 3' }}>
      {/* Brand header — 设计稿：智记 logo + 标题 */}
      <div style={{ padding: '20px 18px 18px', display: 'flex', alignItems: 'center', gap: 10 }}>
        <div style={{
          width: 30, height: 30, background: 'var(--color-accent, #b8452e)',
          borderRadius: 6, display: 'flex', alignItems: 'center', justifyContent: 'center',
          transform: 'rotate(-4deg)', boxShadow: '0 2px 6px rgba(184,69,46,0.3)',
          flexShrink: 0,
        }}>
          <span style={{ fontFamily: 'var(--font-hand, "LXGW WenKai TC", cursive)', color: 'var(--color-paper-0, #fbf7ee)', fontSize: 17, fontWeight: 700 }}>智</span>
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 13, fontWeight: 700, letterSpacing: '0.1em', color: 'var(--color-text, #2b2620)' }}>智记</div>
          <div style={{ fontSize: 10, color: 'var(--color-ink-400, #9a8b73)' }}>你 · 的笔记本</div>
        </div>
      </div>

      {/* Search — 设计稿：搜索或问 AI… + ⌘K */}
      <div style={{ padding: '0 14px 14px' }}>
        <div style={{
          background: 'var(--color-paper-0, #fbf7ee)', borderRadius: 8, padding: '7px 10px',
          display: 'flex', alignItems: 'center', gap: 8,
          border: '1px solid var(--color-border, #e0d4b8)',
        }}>
          <MagnifyingGlassIcon className="w-3.5 h-3.5 shrink-0" style={{ color: 'var(--color-ink-400, #9a8b73)' }} />
          <input
            type="text"
            aria-label="搜索笔记或问 AI"
            value={ui.searchQuery}
            onChange={(e) => handleSearch(e.target.value)}
            placeholder="搜索或问 AI…"
            style={{
              border: 'none', background: 'transparent', flex: 1, fontSize: 12,
              outline: 'none', fontFamily: 'var(--font-body)', color: 'var(--color-text, #2b2620)',
            }}
          />
          {ui.searchQuery ? (
            <button
              type="button"
              aria-label="Clear search"
              onClick={ui.clearSearch}
              style={{ border: 'none', background: 'transparent', cursor: 'pointer', padding: 0, display: 'flex', color: 'var(--color-ink-400, #9a8b73)' }}
            >
              <XMarkIcon className="w-3.5 h-3.5" />
            </button>
          ) : (
            <span style={{
              fontSize: 10, color: 'var(--color-ink-400, #9a8b73)',
              background: 'var(--color-paper-3, #ede5d1)', padding: '1px 5px', borderRadius: 3,
              flexShrink: 0,
            }}>⌘K</span>
          )}
        </div>
      </div>

      {/* Quick shortcuts */}
      <div style={{ padding: '0 8px', marginBottom: 8 }}>
        {shortcuts.map(item => (
          <SidebarRowButton
            key={item.id}
            ariaLabel={`Show ${item.label}`}
            active={ui.sidebarFilter === item.id}
            style={{
              display: 'flex', alignItems: 'center', gap: 8,
              padding: '6px 10px', borderRadius: 6, cursor: 'pointer', fontSize: 13,
              color: item.accent ? 'var(--color-accent, #b8452e)' : 'var(--color-text-secondary, #7a6b58)',
              background: ui.sidebarFilter === item.id ? 'rgba(74,64,50,0.1)' : 'transparent',
              transition: 'background .12s',
            }}
            onMouseEnter={e => { if (ui.sidebarFilter !== item.id) e.currentTarget.style.background = 'rgba(74,64,50,0.06)'; }}
            onMouseLeave={e => { if (ui.sidebarFilter !== item.id) e.currentTarget.style.background = 'transparent'; }}
            onClick={() => {
              ui.setSidebarFilter(item.id);
              ui.setSelectedFolderId(null);
            }}
          >
            <span style={{ width: 16, textAlign: 'center', fontSize: 12 }}>{item.icon}</span>
            <span style={{ flex: 1 }}>{item.label}</span>
            <span style={{ fontSize: 11, color: 'var(--color-ink-400, #9a8b73)' }}>{item.count}</span>
          </SidebarRowButton>
        ))}
      </div>

      {/* 笔记本 分隔线 — 设计稿：带 + 新建按钮 */}
      <div style={{
        margin: '8px 18px', fontSize: 10, letterSpacing: '0.2em',
        color: 'var(--color-ink-400, #9a8b73)', display: 'flex', alignItems: 'center', gap: 8,
      }}>
        <span>笔记本</span>
        <div style={{ flex: 1, height: 1, background: 'var(--color-border, #e0d4b8)' }} />
        <SidebarIconButton
          aria-label="Create folder"
          title="Create folder"
          onClick={() => handleCreateFolder()}
          style={{ color: 'var(--color-ink-400, #9a8b73)', display: 'flex' }}
        >
          <PlusIcon className="w-3.5 h-3.5" />
        </SidebarIconButton>
      </div>

      {/* Search results */}
      {ui.isSearching && ui.searchResults.length > 0 ? (
        <div className="flex-1 overflow-hidden flex flex-col">
          <div style={{
            padding: '8px 18px', fontSize: 12, fontWeight: 600,
            color: 'var(--color-text-secondary, #7a6b58)',
            borderBottom: '1px solid var(--color-border, #e0d4b8)',
          }}>
            搜索结果 ({ui.searchResults.length})
          </div>
          <div ref={searchResultsRef} style={{ flex: 1, minHeight: 0 }}>
            <FixedSizeList
              height={searchResultsHeight}
              width="100%"
              itemCount={ui.searchResults.length}
              itemSize={SEARCH_RESULT_ROW_HEIGHT}
              itemKey={(index) => ui.searchResults[index].id}
            >
              {renderSearchResult}
            </FixedSizeList>
          </div>
        </div>
      ) : (
        /* Folder list (notebooks) */
        <div style={{ flex: 1, overflowY: 'auto', padding: '0 8px' }}>
          {folders.map((folder) => {
            const isActive = ui.selectedFolderId === folder.id;
            const folderNoteCount = notes.filter(n => n.folderId === folder.id).length;
            return (
              <div
                key={folder.id}
                style={{
                  display: 'flex', alignItems: 'center', gap: 8,
                  padding: '7px 10px', borderRadius: 6, fontSize: 13,
                  background: isActive ? 'var(--color-paper-0, #fbf7ee)' : 'transparent',
                  boxShadow: isActive ? '0 1px 3px rgba(74,64,50,0.08), inset 0 0 0 1px var(--color-border, #e0d4b8)' : 'none',
                  fontWeight: isActive ? 600 : 400,
                  marginBottom: 2, transition: 'all .12s',
                  color: 'var(--color-text, #2b2620)',
                }}
                className="sidebar-folder-row"
              >
                <SidebarRowButton
                  ariaLabel={`Open folder ${folder.name}`}
                  active={isActive}
                  style={{
                    display: 'flex', alignItems: 'center', gap: 8,
                    flex: 1, minWidth: 0, padding: 0, cursor: 'pointer',
                    color: 'inherit', fontSize: 13, fontWeight: 'inherit',
                  }}
                  onClick={() => {
                    ui.setSelectedFolderId(isActive ? null : folder.id);
                    ui.setSidebarFilter('all');
                  }}
                >
                  <span style={{
                    width: 10, height: 10, borderRadius: 2, flexShrink: 0,
                    background: folder.color || 'var(--color-ink-400, #9a8b73)',
                    boxShadow: `0 0 0 1.5px var(--color-paper-0, #fbf7ee), 0 0 0 2px ${(folder.color || '#9a8b73')}30`,
                  }} />
                  <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{folder.name}</span>
                  <span style={{ fontSize: 11, color: 'var(--color-ink-400, #9a8b73)' }}>{folderNoteCount}</span>
                </SidebarRowButton>
                <button
                  type="button"
                  aria-label={`More actions for ${folder.name}`}
                  className="sidebar-folder-more"
                  onClick={(e) => handleFolderMore(e, folder.id)}
                  title="More actions"
                  style={{
                    width: 20, height: 20, borderRadius: 4,
                    border: 'none', background: 'transparent',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    cursor: 'pointer', flexShrink: 0, fontSize: 14,
                    color: 'var(--color-ink-400, #9a8b73)',
                    opacity: ctxMenu?.folderId === folder.id ? 1 : undefined,
                  }}
                >
                  ···
                </button>
              </div>
            );
          })}

          {/* Unfiled notes entry */}
          <SidebarRowButton
            ariaLabel="Open unfiled notes"
            active={ui.selectedFolderId === '__unfiled__'}
            style={{
              display: 'flex', alignItems: 'center', gap: 8,
              padding: '7px 10px', borderRadius: 6, cursor: 'pointer', fontSize: 13,
              background: ui.selectedFolderId === '__unfiled__' ? 'var(--color-paper-0, #fbf7ee)' : 'transparent',
              boxShadow: ui.selectedFolderId === '__unfiled__' ? '0 1px 3px rgba(74,64,50,0.08), inset 0 0 0 1px var(--color-border, #e0d4b8)' : 'none',
              fontWeight: ui.selectedFolderId === '__unfiled__' ? 600 : 400,
              marginBottom: 2, transition: 'all .12s',
              color: 'var(--color-text, #2b2620)',
            }}
            onClick={() => {
              ui.setSelectedFolderId(ui.selectedFolderId === '__unfiled__' ? null : '__unfiled__');
              ui.setSidebarFilter('all');
            }}
          >
            <span style={{
              width: 10, height: 10, borderRadius: 2, flexShrink: 0,
              background: 'var(--color-ink-400, #9a8b73)',
              boxShadow: '0 0 0 1.5px var(--color-paper-0, #fbf7ee), 0 0 0 2px rgba(154,139,115,0.2)',
            }} />
            <span style={{ flex: 1 }}>未分类</span>
            <span style={{ fontSize: 11, color: 'var(--color-ink-400, #9a8b73)' }}>
              {notes.filter(n => !n.folderId).length}
            </span>
          </SidebarRowButton>
        </div>
      )}

      {/* Tags section — 设计稿：标签云 */}
      {allTags.length > 0 && (
        <>
          <div style={{
            padding: '8px 18px 0', fontSize: 10, letterSpacing: '0.2em',
            color: 'var(--color-ink-400, #9a8b73)', marginBottom: 6,
          }}>标签</div>
          <div style={{ padding: '0 14px 14px', display: 'flex', flexWrap: 'wrap', gap: 5 }} className="shrink-0">
            {allTags.map(t => (
              <span key={t} style={{
                fontSize: 11, padding: '2px 8px',
                background: 'var(--color-paper-0, #fbf7ee)',
                borderRadius: 10, border: '1px solid var(--color-border, #e0d4b8)',
                cursor: 'pointer', color: 'var(--color-text-secondary, #7a6b58)',
              }}>#{t}</span>
            ))}
          </div>
        </>
      )}

      {/* Bottom: Tools grouped + User profile — 设计稿方案B */}
      <div style={{ borderTop: '1px solid var(--color-border, #e0d4b8)', flexShrink: 0 }}>
        <div style={{ padding: '8px 8px 4px', display: 'flex', flexDirection: 'column', gap: 1 }}>
          {/* 视图 group */}
          <div style={{ padding: '4px 10px 2px', fontSize: 10, letterSpacing: '0.15em', color: 'var(--color-ink-400, #9a8b73)' }}>视图</div>
          <SidebarToolButton ariaLabel="Open graph view" onClick={() => ui.setIsGraphOpen(true)}>
            <GlobeAltIcon className="w-3.5 h-3.5" />
            <span>关系图谱</span>
          </SidebarToolButton>
          <SidebarToolButton ariaLabel="Open timeline view" onClick={() => ui.setIsTimelineOpen(true)}>
            <ClockIcon className="w-3.5 h-3.5" />
            <span>时间线</span>
          </SidebarToolButton>
          <SidebarToolButton ariaLabel="Open canvas" onClick={() => ui.setIsCanvasOpen(true)}>
            <span style={{ width: 14, display: 'inline-flex', justifyContent: 'center', fontSize: 13 }}>◫</span>
            <span>画布</span>
          </SidebarToolButton>

          {/* 工具 group */}
          <div style={{ padding: '6px 10px 2px', fontSize: 10, letterSpacing: '0.15em', color: 'var(--color-ink-400, #9a8b73)' }}>工具</div>
          <SidebarToolButton ariaLabel="Create schedule" onClick={() => { scheduleStore.setEditingSchedule(undefined); ui.setIsScheduleFormOpen(true); }}>
            <CalendarDaysIcon className="w-3.5 h-3.5" />
            <span>创建日程</span>
          </SidebarToolButton>
          <SidebarToolButton ariaLabel="Open workflows" onClick={() => ui.setIsWorkflowsOpen(true)}>
            <span style={{ width: 14, display: 'inline-flex', justifyContent: 'center', fontSize: 13 }}>⚙</span>
            <span>工作流</span>
          </SidebarToolButton>
          <SidebarToolButton ariaLabel="Open task center" onClick={() => ui.setIsTaskPanelOpen(true)}>
            <span style={{ width: 14, display: 'inline-flex', justifyContent: 'center', fontSize: 13 }}>☰</span>
            <span>任务中心</span>
          </SidebarToolButton>
          <SidebarToolButton ariaLabel="Open scheduled tasks" onClick={() => ui.setIsTaskScheduleOpen(true)}>
            <span style={{ width: 14, display: 'inline-flex', justifyContent: 'center', fontSize: 13 }}>⏰</span>
            <span>定时任务</span>
          </SidebarToolButton>
          <SidebarToolButton ariaLabel="Open evaluation dashboard" onClick={() => ui.setIsEvalDashOpen(true)}>
            <ChartBarIcon className="w-3.5 h-3.5" />
            <span>评估中心</span>
          </SidebarToolButton>
          <SidebarToolButton ariaLabel="Open agent metrics" onClick={() => ui.setIsAgentMetricsOpen(true)}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0 }}>
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
            </svg>
            <span>Agent 指标</span>
          </SidebarToolButton>
          <SidebarToolButton ariaLabel="Open trace panel" onClick={() => ui.setIsTracesPanelOpen(true)}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0 }}>
              <path d="M4 5h16M4 12h16M4 19h16" />
              <path d="M8 5v14M16 5v14" />
            </svg>
            <span>调用追踪</span>
          </SidebarToolButton>

          {/* 其他 group */}
          <div style={{ padding: '6px 10px 2px', fontSize: 10, letterSpacing: '0.15em', color: 'var(--color-ink-400, #9a8b73)' }}>其他</div>
          <SidebarToolButton ariaLabel="Open trash" onClick={handleOpenTrash}>
            <svg width="13" height="13" viewBox="0 0 13 13" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M 2 3.5 L 11 3.5 M 4 3.5 L 4 2.5 Q 4 2, 4.5 2 L 8.5 2 Q 9 2, 9 2.5 L 9 3.5 M 3 3.5 L 3.5 11 Q 3.5 11.5, 4 11.5 L 9 11.5 Q 9.5 11.5, 9.5 11 L 10 3.5" />
            </svg>
            <span>回收站</span>
          </SidebarToolButton>
        </div>

        {/* User profile — 设计稿：头像圆圈 + 姓名 + 副标题 */}
        <div style={{
          padding: '10px 14px', borderTop: '1px solid var(--color-border, #e0d4b8)',
          display: 'flex', alignItems: 'center', gap: 10,
        }}>
          <div style={{
            width: 28, height: 28, borderRadius: '50%',
            background: 'linear-gradient(135deg, #d96b52, #b8452e)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            color: 'var(--color-paper-0, #fbf7ee)', fontSize: 13, fontWeight: 600,
            fontFamily: 'var(--font-hand, "LXGW WenKai TC", cursive)',
            flexShrink: 0,
          }}>{userInitial}</div>
          <div style={{ flex: 1, minWidth: 0, overflow: 'hidden' }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--color-text, #2b2620)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {user?.username || '用户'}
            </div>
            <div style={{ fontSize: 10, color: 'var(--color-ink-400, #9a8b73)' }}>
              {user?.email || '笔记助手'}
            </div>
          </div>
          <SidebarIconButton
            aria-label="Open cost dashboard"
            title="API cost"
            onClick={() => ui.setIsCostDashboardOpen(true)}
            className="shrink-0"
            style={{ color: 'var(--color-ink-400, #9a8b73)', display: 'flex' }}
          >
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" className="w-3.5 h-3.5">
              <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.798 7.45c.512-.67 1.135-.95 1.702-.95s1.19.28 1.702.95a.75.75 0 001.192-.91C12.637 5.55 11.5 5 10.5 5s-2.137.55-2.894 1.54A5.205 5.205 0 006.5 10c0 1.388.443 2.607 1.106 3.46.757.99 1.894 1.54 2.894 1.54s2.137-.55 2.894-1.54a.75.75 0 00-1.192-.91c-.512.67-1.135.95-1.702.95s-1.19-.28-1.702-.95A3.705 3.705 0 018 10c0-.96.293-1.877.798-2.55z" clipRule="evenodd" />
            </svg>
          </SidebarIconButton>
          <SidebarIconButton
            aria-label="Open settings"
            title="Settings"
            onClick={() => ui.setIsSettingsOpen(true)}
            className="shrink-0"
            style={{ color: 'var(--color-ink-400, #9a8b73)', display: 'flex' }}
          >
            <Cog6ToothIcon className="w-3.5 h-3.5" />
          </SidebarIconButton>
          <button
            type="button"
            aria-label="Log out"
            onClick={onLogout}
            title="退出登录"
            style={{
              border: 'none', background: 'transparent', cursor: 'pointer',
              padding: 2, display: 'flex', alignItems: 'center', justifyContent: 'center',
              color: 'var(--color-ink-400, #9a8b73)', borderRadius: 4,
              transition: 'all 0.12s',
            }}
            onMouseEnter={e => { e.currentTarget.style.color = 'var(--color-accent, #b8452e)'; }}
            onMouseLeave={e => { e.currentTarget.style.color = 'var(--color-ink-400, #9a8b73)'; }}
          >
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round">
              <path d="M5 12H3a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1h2M9 10l3-3-3-3M12 7H5" />
            </svg>
          </button>
        </div>
      </div>

      {/* Folder context menu */}
      {ctxMenu && (() => {
        const folder = folders.find(f => f.id === ctxMenu.folderId);
        if (!folder) return null;
        return (
          <div
            role="menu"
            aria-label={`Folder actions for ${folder.name}`}
            ref={folderMenuRef}
            onKeyDown={handleFolderMenuKeyDown}
            style={{
              position: 'fixed', left: ctxMenu.x, top: ctxMenu.y, zIndex: 9999,
              background: 'var(--color-paper-0, #fbf7ee)',
              border: '1px solid var(--color-border, #e0d4b8)',
              borderRadius: 8, padding: '6px 0',
              boxShadow: '0 4px 16px rgba(74,64,50,0.15)',
              minWidth: 160, fontSize: 12,
              color: 'var(--color-text, #2b2620)',
            }}
            onClick={e => e.stopPropagation()}
          >
            {/* Rename */}
            <button
              type="button"
              role="menuitem"
              style={{
                width: '100%',
                border: 'none',
                background: 'transparent',
                textAlign: 'left',
                fontFamily: 'inherit',
                fontSize: 12,
                color: 'inherit',
                padding: '6px 14px',
                cursor: 'pointer',
                transition: 'background .1s',
              }}
              onMouseEnter={e => e.currentTarget.style.background = 'rgba(74,64,50,0.06)'}
              onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
              onClick={() => {
                setRenaming({ id: folder.id, name: folder.name });
                setCtxMenu(null);
              }}
            >
              ✏️ 重命名
            </button>

            {/* Color picker */}
            <div style={{ padding: '6px 14px', fontSize: 11, color: 'var(--color-text-secondary, #9a8b73)' }}>
              🎨 颜色
            </div>
            <div style={{ padding: '2px 14px 6px', display: 'flex', gap: 6, alignItems: 'center' }}>
              {FOLDER_COLORS.map(c => (
                <button
                  type="button"
                  role="menuitem"
                  aria-label={`Set folder color to ${c.name}`}
                  key={c.value}
                  title={c.name}
                  style={{
                    border: 'none',
                    padding: 0,
                    width: 16, height: 16, borderRadius: 4, cursor: 'pointer',
                    background: c.value,
                    outline: folder.color === c.value ? '2px solid var(--color-text, #2b2620)' : 'none',
                    outlineOffset: 1,
                    transition: 'transform .1s',
                  }}
                  onMouseEnter={e => e.currentTarget.style.transform = 'scale(1.2)'}
                  onMouseLeave={e => e.currentTarget.style.transform = 'scale(1)'}
                  onClick={() => {
                    handleUpdateFolderColor(folder.id, c.value);
                    setCtxMenu(null);
                  }}
                />
              ))}
              {folder.color && (
                <button
                  type="button"
                  role="menuitem"
                  aria-label="Clear folder color"
                  title="清除颜色"
                  style={{
                    padding: 0,
                    background: 'transparent',
                    width: 16, height: 16, borderRadius: 4, cursor: 'pointer',
                    border: '1px solid var(--color-border, #e0d4b8)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    fontSize: 10, color: 'var(--color-text-secondary, #9a8b73)',
                  }}
                  onClick={() => {
                    handleUpdateFolderColor(folder.id, null);
                    setCtxMenu(null);
                  }}
                >
                  ✕
                </button>
              )}
            </div>

            <div style={{ height: 1, background: 'var(--color-border, #e0d4b8)', margin: '4px 0' }} />

            {/* Delete */}
            <button
              type="button"
              role="menuitem"
              style={{
                width: '100%',
                border: 'none',
                background: 'transparent',
                textAlign: 'left',
                fontFamily: 'inherit',
                fontSize: 12,
                padding: '6px 14px',
                cursor: 'pointer',
                color: 'var(--color-accent, #b8452e)',
                transition: 'background .1s',
              }}
              onMouseEnter={e => e.currentTarget.style.background = 'rgba(184,69,46,0.06)'}
              onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
              onClick={() => {
                handleDeleteFolder(folder.id);
                setCtxMenu(null);
              }}
            >
              🗑️ 删除文件夹
            </button>
          </div>
        );
      })()}

      {/* Rename dialog */}
      {renaming && (
        <div
          style={{
            position: 'fixed', inset: 0, zIndex: 10000,
            background: 'rgba(0,0,0,0.3)', display: 'flex',
            alignItems: 'center', justifyContent: 'center',
          }}
          onClick={closeRenameDialog}
        >
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="rename-folder-title"
            ref={renameDialogRef}
            tabIndex={-1}
            onKeyDown={handleRenameDialogKeyDown}
            style={{
              background: 'var(--color-paper-0, #fbf7ee)',
              border: '1px solid var(--color-border, #e0d4b8)',
              borderRadius: 10, padding: '20px 24px', width: 300,
              boxShadow: '0 8px 30px rgba(74,64,50,0.2)',
            }}
            onClick={e => e.stopPropagation()}
          >
            <div id="rename-folder-title" style={{ fontSize: 14, fontWeight: 600, marginBottom: 12, color: 'var(--color-text, #2b2620)' }}>
              重命名文件夹
            </div>
            <input
              autoFocus
              value={renaming.name}
              onChange={e => setRenaming({ ...renaming, name: e.target.value })}
              onKeyDown={e => {
                if (e.key === 'Enter' && renaming.name.trim()) {
                  confirmRenameFolder();
                }
              }}
              style={{
                width: '100%', padding: '8px 10px', fontSize: 13,
                border: '1.5px solid var(--color-border, #e0d4b8)',
                borderRadius: 6, outline: 'none', fontFamily: 'inherit',
                background: 'var(--color-paper-0, #fbf7ee)',
                color: 'var(--color-text, #2b2620)',
                boxSizing: 'border-box',
              }}
            />
            <div style={{ display: 'flex', gap: 8, marginTop: 14, justifyContent: 'flex-end' }}>
              <button
                type="button"
                onClick={closeRenameDialog}
                style={{
                  padding: '5px 14px', fontSize: 12, borderRadius: 6, cursor: 'pointer',
                  border: '1px solid var(--color-border, #e0d4b8)',
                  background: 'transparent', color: 'var(--color-text-secondary, #9a8b73)',
                  fontFamily: 'inherit',
                }}
              >
                取消
              </button>
              <button
                type="button"
                onClick={confirmRenameFolder}
                style={{
                  padding: '5px 14px', fontSize: 12, borderRadius: 6, cursor: 'pointer',
                  border: 'none',
                  background: 'var(--color-accent, #b8452e)', color: 'var(--color-paper-0, #fbf7ee)',
                  fontFamily: 'inherit',
                }}
              >
                确定
              </button>
            </div>
          </div>
        </div>
      )}
    </aside>
  );
}
