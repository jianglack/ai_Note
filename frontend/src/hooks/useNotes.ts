import { useEffect, useRef, useState, useCallback } from 'react';
import { useNoteStore } from '../stores/noteStore';
import { useUiStore } from '../stores/uiStore';
import { useAiStore } from '../stores/aiStore';
import {
  getNotes, getFolders, createNote, updateNote, deleteNote,
  createFolder, updateFolder, deleteFolder,
  getTrashNotes, restoreNote, permanentDeleteNote,
  hybridSearch, getSpiritSuggestTags
} from '../api';
import { askConfirm, askPrompt } from '../services/dialogService';
import type { Note } from '../api';

export function useNotes() {
  const store = useNoteStore();
  const ui = useUiStore();
  const ai = useAiStore();

  // Editor local state
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [tags, setTags] = useState<string[]>([]);
  const [tagInput, setTagInput] = useState('');
  const lastSavedRef = useRef('');
  const saveTimerRef = useRef<number | null>(null);

  // Sync selected note → local editor state
  useEffect(() => {
    const note = store.selectedNote;
    if (!note) {
      setTitle('');
      setContent('');
      setTags([]);
      lastSavedRef.current = '';
      return;
    }
    setTitle(note.title);
    setContent(note.content);
    setTags(note.tags?.map((t) => t.name) || []);
    lastSavedRef.current = `${note.title}::${note.content}::${(note.tags || []).map((t) => t.name).join('|')}`;
  }, [store.selectedNote]);

  // Auto-save
  useEffect(() => {
    const note = store.selectedNote;
    if (!note) return;
    const signature = `${title}::${content}::${tags.join('|')}`;
    if (signature === lastSavedRef.current) return;

    if (saveTimerRef.current) window.clearTimeout(saveTimerRef.current);

    saveTimerRef.current = window.setTimeout(async () => {
      try {
        const updated = await updateNote(note.id, {
          title, content, tags, folderId: note.folderId,
        });
        store.updateNoteInList(updated);
        lastSavedRef.current = signature;
        ui.setStatus('已自动保存');
      } catch (err) {
        ui.setStatus((err as Error).message);
      }
    }, 800);

    return () => { if (saveTimerRef.current) window.clearTimeout(saveTimerRef.current); };
  }, [title, content, tags, store.selectedNote]);

  // Load initial data
  const loadData = useCallback(async () => {
    try {
      const [notesData, foldersData] = await Promise.all([getNotes(), getFolders()]);
      store.setNotes(notesData);
      store.setFolders(foldersData);
    } catch (err) {
      ui.setStatus((err as Error).message);
    }
  }, []);

  const handleSelectNote = useCallback((note: Note) => {
    store.setSelectedNote(note);
  }, []);

  const handleCreateNote = useCallback(async (folderId?: string | null) => {
    try {
      const note = await createNote({ title: '无标题', content: '', tags: [], folderId });
      store.addNote(note);
      store.setSelectedNote(note);
      ui.setStatus('已创建笔记');
    } catch (err) {
      ui.setStatus((err as Error).message);
    }
  }, []);

  const handleDeleteNote = useCallback(async (id: string) => {
    if (!await askConfirm({
      title: 'Delete note',
      message: 'Move this note to trash?',
      confirmLabel: 'Delete',
      danger: true,
    })) return;
    try {
      await deleteNote(id);
      store.removeNote(id);
      ui.setStatus('已删除笔记');
    } catch (err) {
      ui.setStatus((err as Error).message);
    }
  }, []);

  const handleCreateFolder = useCallback(async (parentId?: string | null) => {
    const name = await askPrompt({
      title: 'New folder',
      message: 'Folder name',
      confirmLabel: 'Create',
    });
    if (!name) return;
    try {
      const folder = await createFolder(name, parentId);
      store.addFolder(folder);
      ui.setStatus('已创建文件夹');
    } catch (err) {
      ui.setStatus((err as Error).message);
    }
  }, []);

  const handleDeleteFolder = useCallback(async (id: string) => {
    if (!await askConfirm({
      title: 'Delete folder',
      message: 'Notes in this folder will move to root.',
      confirmLabel: 'Delete',
      danger: true,
    })) return;
    try {
      await deleteFolder(id);
      store.removeFolder(id);
      store.moveNotesToRoot(id);
      ui.setStatus('已删除文件夹');
    } catch (err) {
      ui.setStatus((err as Error).message);
    }
  }, []);

  const handleRenameFolder = useCallback(async (id: string, name: string) => {
    try {
      const updated = await updateFolder(id, name);
      store.updateFolderInList(updated);
      ui.setStatus('已重命名');
    } catch (err) {
      ui.setStatus((err as Error).message);
    }
  }, []);

  const handleUpdateFolderColor = useCallback(async (id: string, color: string | null) => {
    const folder = store.folders.find(f => f.id === id);
    if (!folder) return;
    try {
      const updated = await updateFolder(id, folder.name, color);
      store.updateFolderInList(updated);
      ui.setStatus(color ? '已设置颜色' : '已清除颜色');
    } catch (err) {
      ui.setStatus((err as Error).message);
    }
  }, [store.folders]);

  // Tags
  const handleAddTag = useCallback(() => {
    if (!tagInput.trim()) return;
    if (tags.includes(tagInput.trim())) return;
    setTags((prev) => [...prev, tagInput.trim()]);
    setTagInput('');
  }, [tagInput, tags]);

  const handleRemoveTag = useCallback((tag: string) => {
    setTags((prev) => prev.filter((t) => t !== tag));
  }, []);

  const handleSuggestTags = useCallback(async () => {
    const note = store.selectedNote;
    if (!note) { ui.setStatus('请先选择一条笔记'); return; }
    if (!note.title && !note.content) { ui.setStatus('笔记内容为空，无法建议标签'); return; }

    ai.setIsLoadingTags(true);
    ui.setStatus('AI 正在分析笔记内容...');
    try {
      const tagsString = await getSpiritSuggestTags(note.id);
      const suggested = tagsString.split(',').map((t) => t.trim()).filter((t) => t.length > 0);
      ai.setTagSuggestions(suggested);
      ui.setStatus(`AI 建议了 ${suggested.length} 个标签`);
      setTimeout(() => ui.setStatus(''), 3000);
    } catch (err) {
      ui.setStatus('获取标签建议失败：' + (err as Error).message);
    } finally {
      ai.setIsLoadingTags(false);
    }
  }, [store.selectedNote]);

  const handleAddSuggestedTag = useCallback((tag: string) => {
    if (tags.includes(tag)) { ui.setStatus('标签已存在'); return; }
    setTags((prev) => [...prev, tag]);
    ai.removeTagSuggestion(tag);
  }, [tags]);

  // Search
  const handleSearch = useCallback(async (query: string) => {
    ui.setSearchQuery(query);
    if (!query.trim()) { ui.clearSearch(); return; }
    try {
      ui.setIsSearching(true);
      const results = await hybridSearch(query);
      ui.setSearchResults(results);
      ui.setStatus(`找到 ${results.length} 条相关笔记`);
    } catch (err) {
      ui.setStatus('搜索失败：' + (err as Error).message);
      ui.setSearchResults([]);
    }
  }, []);

  // Trash
  const handleOpenTrash = useCallback(async () => {
    try {
      const trash = await getTrashNotes();
      store.setTrashNotes(trash);
      ui.setViewMode('trash');
      ui.setStatus('已打开回收站');
    } catch (err) {
      ui.setStatus('加载回收站失败：' + (err as Error).message);
    }
  }, []);

  const handleCloseTrash = useCallback(() => {
    ui.setViewMode('notes');
    store.setTrashNotes([]);
  }, []);

  const handleRestoreNote = useCallback(async (id: string) => {
    try {
      await restoreNote(id);
      store.removeTrashNote(id);
      const notesData = await getNotes();
      store.setNotes(notesData);
      ui.setStatus('笔记已恢复');
    } catch (err) {
      ui.setStatus('恢复失败：' + (err as Error).message);
    }
  }, []);

  const handlePermanentDelete = useCallback(async (id: string) => {
    try {
      await permanentDeleteNote(id);
      store.removeTrashNote(id);
      ui.setStatus('笔记已永久删除');
    } catch (err) {
      ui.setStatus('删除失败：' + (err as Error).message);
    }
  }, []);

  const handleExportMarkdown = useCallback(() => {
    if (!store.selectedNote) return;
    const blob = new Blob([`# ${title}\n\n${content}`], { type: 'text/markdown;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${title || '无标题'}.md`;
    a.click();
    URL.revokeObjectURL(url);
    ui.setStatus('已导出 Markdown 文件');
  }, [title, content, store.selectedNote]);

  // Helper: resolve noteId by UUID or title
  const resolveNoteId = useCallback((noteIdOrTitle: string | undefined): string | undefined => {
    if (!noteIdOrTitle) return undefined;
    const notes = store.notes;
    if (notes.find((n) => n.id === noteIdOrTitle)) return noteIdOrTitle;
    const byTitle = notes.find((n) => n.title === noteIdOrTitle);
    return byTitle?.id;
  }, [store.notes]);

  const handleTogglePin = useCallback(async () => {
    const note = store.selectedNote;
    if (!note) return;
    try {
      const updated = await updateNote(note.id, {
        title: note.title, content: note.content,
        tags: note.tags?.map(t => t.name) || [],
        folderId: note.folderId,
        pinned: !note.pinned,
      });
      store.updateNoteInList(updated);
      ui.setStatus(updated.pinned ? '已置顶' : '已取��置顶');
    } catch (err) { ui.setStatus((err as Error).message); }
  }, [store.selectedNote]);

  const handleToggleStar = useCallback(async () => {
    const note = store.selectedNote;
    if (!note) return;
    try {
      const updated = await updateNote(note.id, {
        title: note.title, content: note.content,
        tags: note.tags?.map(t => t.name) || [],
        folderId: note.folderId,
        starred: !note.starred,
      });
      store.updateNoteInList(updated);
      ui.setStatus(updated.starred ? '已收藏' : '已取消收藏');
    } catch (err) { ui.setStatus((err as Error).message); }
  }, [store.selectedNote]);

  return {
    // Editor state
    title, setTitle, content, setContent, tags, setTags, tagInput, setTagInput,
    // Data actions
    loadData,
    handleSelectNote, handleCreateNote, handleDeleteNote,
    handleCreateFolder, handleDeleteFolder, handleRenameFolder, handleUpdateFolderColor,
    // Tags
    handleAddTag, handleRemoveTag, handleSuggestTags, handleAddSuggestedTag,
    // Search
    handleSearch,
    // Trash
    handleOpenTrash, handleCloseTrash, handleRestoreNote, handlePermanentDelete,
    // Export
    handleExportMarkdown,
    // Pin/Star
    handleTogglePin, handleToggleStar,
    // Helpers
    resolveNoteId,
  };
}
