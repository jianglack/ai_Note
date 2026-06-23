import { useMemo, useState, useEffect, useCallback, useRef } from 'react';
import {
  CalendarDaysIcon,
  ArrowDownTrayIcon,
  SparklesIcon,
  XMarkIcon,
  StarIcon as StarOutlineIcon,
  EllipsisHorizontalIcon,
} from '@heroicons/react/24/outline';
import { StarIcon as StarSolidIcon } from '@heroicons/react/24/solid';
import { useAiStore } from '../stores/aiStore';
import { useUiStore } from '../stores/uiStore';
import { useNoteStore } from '../stores/noteStore';
import { useAiChat } from '../hooks/useAiChat';
import { useNotes } from '../hooks/useNotes';
import { useNoteEditor } from '../hooks/useNoteEditor';
import { createNote } from '../api';
import TrashView from '../TrashView';
import TiptapEditor from './TiptapEditor';
import AiInlineSuggestion from './AiInlineSuggestion';
import EmptyState from './EmptyState';
import LinksPanel from './features/LinksPanel';
import { sanitizeAnchorUrl, sanitizeEditorHtml, sanitizeFormulaInput, sanitizeImageUrl } from '../security/contentSafety';
import { askPrompt } from '../services/dialogService';

import type { Editor } from '@tiptap/react';

export default function EditorPane() {
  const ai = useAiStore();
  const ui = useUiStore();
  const { notes, selectedNote, trashNotes, folders } = useNoteStore();
  const noteStore = useNoteStore();
  const { handleAiInlineAction, handleAiMessage } = useAiChat();
  const {
    handleSelectNote,
    handleCreateNote,
    handleCloseTrash, handleRestoreNote, handlePermanentDelete,
  } = useNotes();
  const {
    title, setTitle, content, setContent, tags, setTags, tagInput, setTagInput,
    handleAddTag, handleRemoveTag,
    handleSuggestTags, handleAddSuggestedTag,
    handleExportMarkdown,
    handleTogglePin, handleToggleStar,
  } = useNoteEditor();

  const folderName = useMemo(() => {
    if (!selectedNote?.folderId) return null;
    return folders.find(f => f.id === selectedNote.folderId)?.name;
  }, [selectedNote?.folderId, folders]);

  const wordCount = useMemo(() => {
    return content.replace(/<[^>]*>/g, '').replace(/\s+/g, '').length;
  }, [content]);

  const readMinutes = Math.max(1, Math.round(wordCount / 300));

  const createdDate = selectedNote?.createdAt
    ? new Date(selectedNote.createdAt)
    : null;

  const now = new Date();
  const timeStr = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;

  // ── 分页状态 ──
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [animating, setAnimating] = useState(false);
  const [hoverArrow, setHoverArrow] = useState<'left' | 'right' | null>(null);
  const [thumbsOpen, setThumbsOpen] = useState(false);
  const [indicatorFaded, setIndicatorFaded] = useState(false);
  const [tiptapEditor, setTiptapEditor] = useState<Editor | null>(null);
  const [tagInputOpen, setTagInputOpen] = useState(false);
  const importInputRef = useRef<HTMLInputElement>(null);
  const tagInputRef = useRef<HTMLInputElement>(null);
  const fadeTimerRef = useRef<ReturnType<typeof setTimeout>>();
  const pageStageRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (tagInputOpen) {
      tagInputRef.current?.focus();
    }
  }, [tagInputOpen]);

  useEffect(() => {
    setTagInputOpen(false);
  }, [selectedNote?.id]);

  // 翻页逻辑 — 立即更新 currentPage，CSS transition 负责动画
  const goPage = useCallback((delta: number) => {
    const target = currentPage + delta;
    if (target < 0 || target >= totalPages || animating) return;
    setAnimating(true);
    setCurrentPage(target);
    setTimeout(() => setAnimating(false), 320);
  }, [currentPage, totalPages, animating]);

  // 直接跳转页
  const jumpToPage = useCallback((page: number) => {
    if (page === currentPage || page < 0 || page >= totalPages || animating) return;
    setAnimating(true);
    setCurrentPage(page);
    setTimeout(() => setAnimating(false), 320);
  }, [currentPage, totalPages, animating]);

  // 键盘翻页
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      // 编辑器内输入时不触发翻页
      const tag = (e.target as HTMLElement).tagName;
      if (tag === 'INPUT' || tag === 'TEXTAREA') return;
      if ((e.target as HTMLElement).closest('.tiptap-editor')) return;
      if (e.key === 'ArrowLeft') goPage(-1);
      if (e.key === 'ArrowRight') goPage(1);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [goPage]);

  // 浮动指示器淡出（输入3秒后）
  const resetFadeTimer = useCallback(() => {
    setIndicatorFaded(false);
    clearTimeout(fadeTimerRef.current);
    fadeTimerRef.current = setTimeout(() => setIndicatorFaded(true), 3000);
  }, []);

  useEffect(() => {
    resetFadeTimer();
    return () => clearTimeout(fadeTimerRef.current);
  }, [currentPage, resetFadeTimer]);

  // 触控板水平滑动翻页
  const wheelAccum = useRef(0);
  useEffect(() => {
    const el = pageStageRef.current;
    if (!el) return;
    const onWheel = (e: WheelEvent) => {
      if (Math.abs(e.deltaX) < Math.abs(e.deltaY)) return; // 纵向滚动不处理
      wheelAccum.current += e.deltaX;
      if (Math.abs(wheelAccum.current) > 100) {
        goPage(wheelAccum.current > 0 ? 1 : -1);
        wheelAccum.current = 0;
      }
    };
    el.addEventListener('wheel', onWheel, { passive: true });
    return () => el.removeEventListener('wheel', onWheel);
  }, [goPage]);

  // ── CSS Columns 分页：利用 CSS 多列布局自动分页，永不截断文字 ──
  const columnsRef = useRef<HTMLDivElement>(null);
  const COLUMN_W = 576; // 720 - 72*2 padding
  const COLUMN_GAP = 144;
  const PAGE_STEP = COLUMN_W + COLUMN_GAP; // 每页水平偏移

  // 测量列数（= 页数）
  useEffect(() => {
    if (!tiptapEditor) return;

    const recalcPages = () => {
      const el = columnsRef.current;
      if (!el) return;
      const pages = Math.max(1, Math.round(el.scrollWidth / PAGE_STEP));
      setTotalPages(pages);
    };

    tiptapEditor.on('update', recalcPages);
    const timer = setTimeout(recalcPages, 150);
    requestAnimationFrame(recalcPages);

    const el = columnsRef.current;
    let ro: ResizeObserver | null = null;
    if (el) {
      ro = new ResizeObserver(recalcPages);
      ro.observe(el);
    }

    return () => {
      tiptapEditor.off('update', recalcPages);
      clearTimeout(timer);
      ro?.disconnect();
    };
  }, [tiptapEditor]);

  // currentPage 越界修正
  useEffect(() => {
    if (currentPage >= totalPages) setCurrentPage(Math.max(0, totalPages - 1));
  }, [totalPages, currentPage]);

  // AI FAB
  const aiFab = !ui.isAiChatOpen && (
    <button
      onClick={() => ui.setIsAiChatOpen(true)}
      aria-label="Open AI assistant"
      title="打开 AI 助手"
      className="editor-ai-fab"
    >
      <svg width="20" height="20" viewBox="0 0 14 14" fill="none" stroke="currentColor" strokeWidth="1.3" strokeLinejoin="round">
        <path d="M 7 1 L 8 5 L 12 6 L 8 7 L 7 11 L 6 7 L 2 6 L 6 5 Z" fill="currentColor" />
      </svg>
    </button>
  );

  const handleAiStart = useCallback(() => {
    ui.setIsAiChatOpen(true);
    handleAiMessage('请帮我起草一篇新的笔记开头。先问我一个简短问题来确定主题，然后给出一个可直接编辑的开头。');
  }, [handleAiMessage, ui]);

  const titleFromFileName = useCallback((fileName: string) => {
    return fileName.replace(/\.(md|markdown|txt)$/i, '').trim() || '导入的笔记';
  }, []);

  const handleImportFiles = useCallback(async (files: FileList | null) => {
    if (!files || files.length === 0) return;

    const supported = Array.from(files).filter(file =>
      /\.(md|markdown|txt)$/i.test(file.name) || file.type.startsWith('text/')
    );

    if (supported.length === 0) {
      ui.setStatus('请选择 Markdown 或文本文件');
      return;
    }

    ui.setStatus(`正在导入 ${supported.length} 个文件...`);

    try {
      const imported = [];
      for (const file of supported) {
        const text = await file.text();
        const note = await createNote({
          title: titleFromFileName(file.name),
          content: text,
          tags: [],
          folderId: null,
        });
        imported.push(note);
      }

      imported.forEach(note => noteStore.addNote(note));
      const last = imported[imported.length - 1];
      if (last) noteStore.setSelectedNote(last);
      ui.setStatus(`已导入 ${imported.length} 条笔记`);
    } catch (err) {
      ui.setStatus('导入失败：' + (err as Error).message);
    } finally {
      if (importInputRef.current) importInputRef.current.value = '';
    }
  }, [noteStore, titleFromFileName, ui]);

  if (ui.viewMode === 'trash') {
    return (
      <main className="editor flex flex-col overflow-y-auto min-h-0 relative">
        <TrashView
          notes={trashNotes}
          onClose={handleCloseTrash}
          onRestore={handleRestoreNote}
          onPermanentDelete={handlePermanentDelete}
        />
        {aiFab}
      </main>
    );
  }

  if (!selectedNote) {
    return (
      <main className="editor flex flex-col overflow-y-auto min-h-0 relative" style={{ background: '#f3eee2' }}>
        <input
          ref={importInputRef}
          type="file"
          accept=".md,.markdown,.txt,text/markdown,text/plain"
          multiple
          style={{ display: 'none' }}
          onChange={(event) => handleImportFiles(event.target.files)}
        />
        <EmptyState
          onCreateNote={() => handleCreateNote()}
          onAiStart={handleAiStart}
          onImport={() => importInputRef.current?.click()}
        />
        {aiFab}
      </main>
    );
  }

  return (
    <main className="editor flex flex-col min-h-0 relative" style={{ background: '#f3eee2', overflow: 'hidden' }}>
      {/* Top action bar */}
      <div className="paper-top-action-bar">
        {folderName && (
          <span style={{ fontSize: 12, color: 'var(--color-text-secondary)', marginRight: 'auto' }}>
            {folderName} ›
          </span>
        )}
        <div style={{ flex: 1 }} />

        <TopIconBtn
          active={selectedNote?.starred}
          onClick={handleToggleStar}
          title={selectedNote?.starred ? '取消收藏' : '收藏'}
        >
          {selectedNote?.starred
            ? <StarSolidIcon style={{ width: 16, height: 16 }} />
            : <StarOutlineIcon style={{ width: 16, height: 16 }} />
          }
        </TopIconBtn>

        <TopIconBtn onClick={() => ui.setIsMindMapOpen(true)} title="关系图谱">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="6" cy="6" r="2" /><circle cx="6" cy="18" r="2" /><circle cx="18" cy="12" r="2" />
            <path d="M6 8v8M6 12h8M14 12a4 4 0 0 0 4-4" />
          </svg>
        </TopIconBtn>

        <TopIconBtn
          active={ui.isLinksPanelOpen}
          onClick={() => ui.setIsLinksPanelOpen(!ui.isLinksPanelOpen)}
          title="复制链接"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
            <path d="M10 13a5 5 0 0 0 7 0l3-3a5 5 0 0 0-7-7l-1 1" />
            <path d="M14 11a5 5 0 0 0-7 0l-3 3a5 5 0 0 0 7 7l1-1" />
          </svg>
        </TopIconBtn>

        <TopIconBtn
          active={ui.isAiChatOpen}
          filled
          onClick={() => ui.setIsAiChatOpen(!ui.isAiChatOpen)}
          title="AI 对话"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
            <path d="M21 12a8 8 0 0 1-12 7l-5 1 1-4A8 8 0 1 1 21 12z" />
          </svg>
        </TopIconBtn>

        <div style={{ width: 8 }} />

        <button className="paper-action-pill" onClick={() => ui.setIsExtractDialogOpen(true)} title="提取日程">
          <CalendarDaysIcon style={{ width: 14, height: 14 }} />
          日程
        </button>

        <button className="paper-action-pill" onClick={handleExportMarkdown} title="导出 Markdown">
          <ArrowDownTrayIcon style={{ width: 14, height: 14 }} />
          导出
        </button>

        <div style={{ width: 8 }} />

        <TopIconBtn onClick={() => {}} title="更多">
          <EllipsisHorizontalIcon style={{ width: 16, height: 16 }} />
        </TopIconBtn>
      </div>

      {/* Toolbar — fixed above paper, outside scroll container */}
      <div style={{ display: 'flex', justifyContent: 'center', padding: '0 40px', flexShrink: 0 }}>
        <PaginatedToolbar editor={tiptapEditor} timeStr={timeStr} />
      </div>

      {/* Paginated paper area */}
      <div className="paginated-scroll-container" onMouseMove={resetFadeTimer}>
        {/* Paper stage */}
        <div className="paper-stage" ref={pageStageRef}>
          {/* Hover zones for arrows */}
          {currentPage > 0 && (
            <div
              className="page-arrow-zone left"
              onMouseEnter={() => setHoverArrow('left')}
              onMouseLeave={() => setHoverArrow(null)}
            />
          )}
          {currentPage < totalPages - 1 && (
            <div
              className="page-arrow-zone right"
              onMouseEnter={() => setHoverArrow('right')}
              onMouseLeave={() => setHoverArrow(null)}
            />
          )}

          {/* Arrow buttons */}
          {currentPage > 0 && (
            <button
              className={`page-arrow-btn left${hoverArrow === 'left' ? ' hovered' : ''}`}
              aria-label="Go to previous page"
              onClick={() => goPage(-1)}
              title="上一页 ←"
              style={{ opacity: hoverArrow === 'left' ? 1 : 0 }}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
                <path d="M15 18l-6-6 6-6" />
              </svg>
            </button>
          )}
          {currentPage < totalPages - 1 && (
            <button
              className={`page-arrow-btn right${hoverArrow === 'right' ? ' hovered' : ''}`}
              aria-label="Go to next page"
              onClick={() => goPage(1)}
              title="下一页 →"
              style={{ opacity: hoverArrow === 'right' ? 1 : 0 }}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
                <path d="M9 18l6-6-6-6" />
              </svg>
            </button>
          )}

          {/* Paper page */}
          <div className="paper-page">
            <div className="paper-accent-strip" />
            <div className="paper-page-inner">
              {/* CSS Columns 视口 — 固定高度，overflow hidden */}
              <div className="page-content-viewport">
                <div
                  ref={columnsRef}
                  className="page-columns"
                  style={{
                    transform: `translateX(-${currentPage * PAGE_STEP}px)`,
                  }}
                >
                  {/* Title area — 始终渲染，translateY 会在第2页把它移出视区 */}
                  <div style={{ marginBottom: 24 }}>
                    <input
                      className="paper-title-input"
                      value={title}
                      onChange={(e) => setTitle(e.target.value)}
                      placeholder="无标题"
                    />
                    <div className="paper-meta-row">
                      {createdDate && (
                        <span>
                          {createdDate.getFullYear()} · {createdDate.getMonth() + 1} · {createdDate.getDate()} · {['周日','周一','周二','周三','周四','周五','周六'][createdDate.getDay()]} · {createdDate.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}
                        </span>
                      )}
                      <span className="paper-meta-dot" />
                      <span>{wordCount} 字 · {readMinutes} 分钟读完</span>
                      {selectedNote?.pinned && (
                        <>
                          <span className="paper-meta-dot" />
                          <span style={{ color: 'var(--color-accent)' }}>已置顶</span>
                        </>
                      )}
                      <span className="paper-meta-dot" />
                      <div style={{ display: 'flex', alignItems: 'center', gap: 4, flexWrap: 'wrap' }}>
                        {tags.map((tag) => (
                          <span key={tag} className="paper-tag">
                            {tag}
                            <button className="paper-tag-remove" onClick={() => handleRemoveTag(tag)}>
                              <XMarkIcon style={{ width: 10, height: 10 }} />
                            </button>
                          </span>
                        ))}
                        <button
                          type="button"
                          className="paper-add-tag-btn"
                          aria-label="添加标签"
                          onClick={() => setTagInputOpen(true)}
                        >
                          <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><path d="M12 5v14M5 12h14"/></svg>
                          添加标签
                        </button>
                      </div>
                    </div>
                    <input
                      id="paper-tag-input"
                      ref={tagInputRef}
                      style={{
                        border: 'none', outline: 'none', background: 'transparent',
                        fontSize: 12, width: 140, marginTop: 4,
                        color: 'var(--color-text-secondary)',
                        display: tagInputOpen || tagInput ? 'block' : 'none',
                      }}
                      placeholder="输入标签后回车..."
                      value={tagInput}
                      onChange={(e) => setTagInput(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          e.preventDefault();
                          handleAddTag();
                          setTagInputOpen(false);
                        }
                        if (e.key === 'Escape') {
                          setTagInput('');
                          setTagInputOpen(false);
                        }
                      }}
                      onBlur={() => { if (!tagInput) setTagInputOpen(false); }}
                    />
                    {ai.tagSuggestions.length > 0 && (
                      <div style={{ display: 'flex', gap: 4, marginTop: 8, flexWrap: 'wrap', alignItems: 'center' }}>
                        <span style={{ fontSize: 11, color: 'var(--color-text-secondary)' }}>AI 建议：</span>
                        {ai.tagSuggestions.map((tag) => (
                          <button key={tag} className="paper-tag-suggestion" onClick={() => handleAddSuggestedTag(tag)}>
                            + {tag}
                          </button>
                        ))}
                        <button
                          className="paper-tag-suggestion"
                          style={{ borderStyle: 'solid', background: 'rgba(184,69,46,0.06)', color: 'var(--color-accent)', borderColor: 'rgba(184,69,46,0.25)' }}
                          onClick={handleSuggestTags}
                          disabled={!selectedNote || ai.isLoadingTags}
                        >
                          <SparklesIcon style={{ width: 10, height: 10 }} />
                          {ai.isLoadingTags ? '分析中...' : 'AI 建议'}
                        </button>
                      </div>
                    )}
                    <div className="paper-title-divider" />
                  </div>
                <TiptapEditor
                  content={content}
                  onChange={setContent}
                  onAiAction={handleAiInlineAction}
                  allNotes={notes}
                  noteId={selectedNote?.id}
                  hideToolbar
                  onEditorReady={setTiptapEditor}
                  onSlashAiAction={(action) => {
                    if (action === 'mindmap') ui.setIsMindMapOpen(true);
                    else if (action === 'continue') handleAiInlineAction('expand', content.slice(-200));
                    else if (action === 'summarize') handleAiInlineAction('summarize', content);
                    else if (action === 'related') ui.setStatus('正在搜索相关笔记...');
                  }}
                  onWikiLinkClick={(wikiTitle) => {
                    const target = notes.find(n => n.title === wikiTitle);
                    if (target) { handleSelectNote(target); ui.setViewMode('notes'); }
                    else ui.setStatus(`未找到笔记「${wikiTitle}」`);
                  }}
                  onAddTags={(newTags) => {
                    setTags((prev) => {
                      const merged = [...prev];
                      newTags.forEach(tag => { if (!merged.includes(tag)) merged.push(tag); });
                      return merged;
                    });
                    ui.setStatus(`已添加标签: ${newTags.join(', ')}`);
                  }}
                />

                {ai.inlineSuggestion && (
                  <div style={{ marginTop: 16 }}>
                    <AiInlineSuggestion
                      noteTitle={ai.inlineSuggestion.noteTitle}
                      noteDate={ai.inlineSuggestion.noteDate}
                      description={ai.inlineSuggestion.description}
                      onLink={() => {
                        const target = notes.find(n => n.id === ai.inlineSuggestion?.noteId);
                        if (target) { handleSelectNote(target); ui.setViewMode('notes'); }
                        ai.setInlineSuggestion(null);
                      }}
                      onDismiss={() => ai.setInlineSuggestion(null)}
                    />
                  </div>
                )}

                {wordCount === 0 && (
                  <div className="paper-quick-start">
                    <div className="paper-quick-start-label">快速开始 · QUICK START</div>
                    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                      {[
                        { icon: 'sparkle', label: '让墨子帮我起草' },
                        { icon: 'todo', label: '插入待办清单' },
                        { icon: 'quote', label: '引用一段文字' },
                        { icon: 'image', label: '插入图片' },
                        { icon: 'code', label: '代码块' },
                      ].map(o => (
                        <button key={o.label} className="paper-quick-chip" onClick={() => {
                          if (o.icon === 'sparkle') ui.setIsAiChatOpen(true);
                        }}>
                          <QuickChipIcon name={o.icon} />
                          {o.label}
                        </button>
                      ))}
                    </div>
                  </div>
                )}
                </div>{/* close page-columns div */}
              </div>{/* close page-content-viewport div */}

              {/* Page number + dots — click to toggle thumbnail strip */}
              <div className="paper-page-number" onClick={() => totalPages > 1 && setThumbsOpen(o => !o)} style={{ cursor: totalPages > 1 ? 'pointer' : undefined }}>
                <span className="paper-page-number-text">— 第 {currentPage + 1} / {totalPages} 页 —</span>
                {totalPages <= 7 && totalPages > 1 && (
                  <div className="paper-page-dots">
                    {Array.from({ length: totalPages }).map((_, i) => (
                      <button
                        key={i}
                        className={`paper-page-dot${i === currentPage ? ' active' : ''}`}
                        onClick={() => jumpToPage(i)}
                      />
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>

        {/* Floating page indicator — click to toggle thumbnails */}
        <div
          className={`floating-page-indicator${indicatorFaded ? ' faded' : ''}`}
          onClick={() => totalPages > 1 && setThumbsOpen(o => !o)}
          style={{ cursor: totalPages > 1 ? 'pointer' : undefined }}
        >
          <span style={{ fontFamily: 'var(--font-serif)', fontWeight: 600, color: 'var(--color-text, #2b2620)' }}>
            第 {currentPage + 1} 页
          </span>
          <span className="indicator-divider" />
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--color-text-secondary)' }}>
            <span className="keycap">←</span>
            <span className="keycap">→</span>
            <span>翻页</span>
          </span>
        </div>

        {/* Thumbnail strip */}
        {thumbsOpen && totalPages > 1 && (
          <div className="thumbnail-strip">
            {Array.from({ length: totalPages }).map((_, i) => (
              <div
                key={i}
                className={`thumbnail-page${i === currentPage ? ' active' : ''}`}
                onClick={() => { jumpToPage(i); setThumbsOpen(false); }}
              >
                <div className="thumb-accent" />
                {/* 真实内容缩小预览 */}
                <div className="thumb-content-wrapper">
                  <div
                    className="thumb-content-mirror"
                    style={{ transform: `translateX(-${i * PAGE_STEP}px)` }}
                    dangerouslySetInnerHTML={{ __html: sanitizeEditorHtml(tiptapEditor?.getHTML() || '') }}
                  />
                </div>
                <span className="thumb-number">{i + 1}</span>
              </div>
            ))}
            <button className="thumb-close-btn" aria-label="Close thumbnails" onClick={() => setThumbsOpen(false)}>
              <XMarkIcon style={{ width: 14, height: 14 }} />
            </button>
          </div>
        )}
      </div>

      {aiFab}

      {/* Links side panel */}
      {ui.isLinksPanelOpen && selectedNote && (
        <div style={{
          position: 'absolute', right: 0, top: 0, bottom: 0, width: 280,
          borderLeft: '1px solid var(--color-border, #e0d4b8)',
          background: 'var(--color-sidebar, #f5efe0)',
          zIndex: 30, overflowY: 'auto',
        }}>
          <LinksPanel />
        </div>
      )}
    </main>
  );
}

/* ── Top icon button (action bar) ── */
function TopIconBtn({ children, onClick, title, active, filled }: {
  children: React.ReactNode; onClick: () => void; title: string; active?: boolean; filled?: boolean;
}) {
  const isFilledActive = filled && active;
  return (
    <button
      onClick={onClick}
      aria-label={title}
      title={title}
      className={`paper-top-icon${active ? ' active' : ''}${isFilledActive ? ' filled' : ''}`}
    >
      {children}
    </button>
  );
}

/* ── Paginated toolbar (above paper, 720px independent card) ── */
function PaginatedToolbar({ editor, timeStr }: { editor: Editor | null; timeStr: string }) {
  if (!editor) return <div className="paginated-toolbar" style={{ height: 44 }} />;

  const btn = (active: boolean) => `tb-btn${active ? ' is-active' : ''}`;

  return (
    <div className="paginated-toolbar" role="toolbar" aria-label="Editor page toolbar">
      <button className={btn(editor.isActive('bold'))} aria-label="Toggle bold" onClick={() => editor.chain().focus().toggleBold().run()} title="加粗">
        <span style={{ fontFamily: 'var(--font-serif)', fontSize: 14, fontWeight: 700 }}>B</span>
      </button>
      <button className={btn(editor.isActive('italic'))} onClick={() => editor.chain().focus().toggleItalic().run()} title="斜体">
        <span style={{ fontFamily: 'var(--font-serif)', fontSize: 14, fontStyle: 'italic' }}>I</span>
      </button>
      <button className={btn(editor.isActive('strike'))} onClick={() => editor.chain().focus().toggleStrike().run()} title="删除线">
        <span style={{ fontFamily: 'var(--font-serif)', fontSize: 14, textDecoration: 'line-through' }}>S</span>
      </button>
      <button className={btn(editor.isActive('underline'))} onClick={() => editor.chain().focus().toggleUnderline().run()} title="下划线">
        <span style={{ fontFamily: 'var(--font-serif)', fontSize: 14, textDecoration: 'underline', textUnderlineOffset: 2 }}>U</span>
      </button>
      <span className="tb-divider" />
      <button className={btn(editor.isActive('highlight'))} onClick={() => editor.chain().focus().toggleHighlight().run()} title="高亮">
        <span style={{ position: 'relative', display: 'inline-flex' }}>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
            <path d="M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"/>
          </svg>
          <span style={{ position: 'absolute', left: 0, right: 0, bottom: -2, height: 2, background: '#f0d878', borderRadius: 1 }} />
        </span>
      </button>
      <button className="tb-btn" title="文字颜色" style={{ position: 'relative' }}>
        <input type="color" style={{ position: 'absolute', inset: 0, opacity: 0, cursor: 'pointer', width: '100%', height: '100%' }}
          value={editor.getAttributes('textStyle').color || '#000000'}
          onChange={(e) => editor.chain().focus().setColor(e.target.value).run()}
        />
        <span style={{ display: 'inline-flex', flexDirection: 'column', alignItems: 'center' }}>
          <span style={{ fontSize: 13, fontWeight: 700, lineHeight: 1 }}>A</span>
          <span style={{ width: 12, height: 2, background: 'var(--color-accent)', borderRadius: 1, marginTop: 1 }} />
        </span>
      </button>
      <span className="tb-divider" />
      <button className={btn(editor.isActive('link'))} aria-label="Insert link" onClick={async () => {
        const url = await askPrompt({
          title: '插入链接',
          message: '输入链接 URL:',
          placeholder: 'https://example.com',
          confirmLabel: '插入',
        });
        const href = sanitizeAnchorUrl(url);
        if (href) editor.chain().focus().setLink({ href }).run();
      }} title="链接">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
          <path d="M10 13a5 5 0 0 0 7 0l3-3a5 5 0 0 0-7-7l-1 1"/><path d="M14 11a5 5 0 0 0-7 0l-3 3a5 5 0 0 0 7 7l1-1"/>
        </svg>
      </button>
      <button className="tb-btn" aria-label="Insert image" onClick={async () => {
        const url = await askPrompt({
          title: '插入图片',
          message: '图片 URL:',
          placeholder: 'https://example.com/image.png',
          confirmLabel: '插入',
        });
        const src = sanitizeImageUrl(url);
        if (src) editor.chain().focus().setImage({ src }).run();
      }} title="图片">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
          <rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3 11h18"/>
        </svg>
      </button>
      <span className="tb-divider" />
      <button className={btn(editor.isActive('heading', { level: 1 }))} onClick={() => editor.chain().focus().toggleHeading({ level: 1 }).run()} title="H1">
        <span style={{ fontFamily: 'var(--font-serif)', fontSize: 12, fontWeight: 600 }}>H1</span>
      </button>
      <button className={btn(editor.isActive('heading', { level: 2 }))} onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()} title="H2">
        <span style={{ fontFamily: 'var(--font-serif)', fontSize: 12, fontWeight: 600 }}>H2</span>
      </button>
      <button className={btn(editor.isActive('heading', { level: 3 }))} onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()} title="H3">
        <span style={{ fontFamily: 'var(--font-serif)', fontSize: 12, fontWeight: 600 }}>H3</span>
      </button>
      <span className="tb-divider" />
      <button className={btn(editor.isActive('bulletList'))} onClick={() => editor.chain().focus().toggleBulletList().run()} title="无序列表">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round">
          <circle cx="5" cy="6" r="1.4" fill="currentColor"/><circle cx="5" cy="12" r="1.4" fill="currentColor"/><circle cx="5" cy="18" r="1.4" fill="currentColor"/>
          <path d="M10 6h11M10 12h11M10 18h11"/>
        </svg>
      </button>
      <button className={btn(editor.isActive('orderedList'))} onClick={() => editor.chain().focus().toggleOrderedList().run()} title="有序列表">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round">
          <path d="M10 6h11M10 12h11M10 18h11"/><path d="M3 5h2v3M3 8h2M3 11h2.5M3 14.5h2A1.5 1.5 0 1 1 4 17H3M3 20h2"/>
        </svg>
      </button>
      <button className={btn(editor.isActive('taskList'))} onClick={() => editor.chain().focus().toggleTaskList().run()} title="待办列表">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
          <rect x="3" y="5" width="6" height="6" rx="1"/><path d="m4.5 8 1.2 1.2L7.5 7.4"/><rect x="3" y="14" width="6" height="6" rx="1"/><path d="M12 8h10M12 17h10"/>
        </svg>
      </button>
      <span className="tb-divider" />
      <button className={btn(editor.isActive('codeBlock'))} onClick={() => editor.chain().focus().toggleCodeBlock().run()} title="代码块">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
          <path d="m8 8-4 4 4 4M16 8l4 4-4 4M14 5l-4 14"/>
        </svg>
      </button>
      <button className="tb-btn" onClick={async () => {
        const f = await askPrompt({
          title: '插入公式',
          message: '输入数学公式 (LaTeX):',
          confirmLabel: '插入',
        });
        const formula = sanitizeFormulaInput(f);
        if (formula) editor.chain().focus().insertContent({ type: 'mathematics', attrs: { formula } }).run();
      }} title="公式">
        <span style={{ fontFamily: 'var(--font-serif)', fontSize: 14, fontStyle: 'italic' }}>Σ</span>
      </button>
      <button className={btn(editor.isActive('blockquote'))} onClick={() => editor.chain().focus().toggleBlockquote().run()} title="引用">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6">
          <path d="M5 6h4l-1 6H5zM14 6h4l-1 6h-3z"/>
        </svg>
      </button>
      <button className="tb-btn" onClick={() => editor.chain().focus().setHorizontalRule().run()} title="分割线">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round">
          <path d="M3 12h18M3 6h12M3 18h12"/>
        </svg>
      </button>
      <span style={{ flex: 1 }} />
      <span style={{ fontFamily: 'var(--font-mono)', fontSize: 10.5, color: 'var(--color-text-secondary)' }}>已自动保存 · {timeStr}</span>
    </div>
  );
}

/* Quick chip icons */
function QuickChipIcon({ name }: { name: string }) {
  const s = { width: 12, height: 12, flexShrink: 0 } as const;
  switch (name) {
    case 'sparkle':
      return <svg {...s} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round">
        <path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M5.6 18.4l2.1-2.1M16.3 7.7l2.1-2.1"/>
      </svg>;
    case 'todo':
      return <svg {...s} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="5" width="6" height="6" rx="1"/><path d="m4.5 8 1.2 1.2L7.5 7.4"/><rect x="3" y="14" width="6" height="6" rx="1"/><path d="M12 8h10M12 17h10"/>
      </svg>;
    case 'quote':
      return <svg {...s} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M5 6h4l-1 6H5zM14 6h4l-1 6h-3z"/></svg>;
    case 'image':
      return <svg {...s} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3 11h18"/>
      </svg>;
    case 'code':
      return <svg {...s} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
        <path d="m8 8-4 4 4 4M16 8l4 4-4 4M14 5l-4 14"/>
      </svg>;
    default: return null;
  }
}
