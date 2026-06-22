import { useCallback } from 'react';
import { useNoteStore } from '../stores/noteStore';
import { useUiStore } from '../stores/uiStore';
import { useAiStore } from '../stores/aiStore';
import {
  createNote, updateNote, deleteNote, getNotes, getFolders, createFolder,
  type Note,
} from '../api';

export function useDialogs() {
  const noteStore = useNoteStore();
  const ui = useUiStore();
  const ai = useAiStore();

  // ========== 笔记选择对话框 ==========
  const handleToggleNoteSelection = useCallback((noteId: string) => {
    ui.toggleNoteSelection(noteId);
  }, []);

  const handleSelectAllNotes = useCallback(() => {
    const ctx = useUiStore.getState().noteSelectionContext;
    if (!ctx) return;
    ui.setSelectedNoteIds(new Set(ctx.candidates.map(n => n.id)));
  }, []);

  const handleDeselectAllNotes = useCallback(() => {
    ui.setSelectedNoteIds(new Set());
  }, []);

  const handleConfirmNoteSelection = useCallback(async () => {
    const { noteSelectionContext, selectedNoteIds } = useUiStore.getState();
    if (!noteSelectionContext || selectedNoteIds.size === 0) {
      ui.setStatus('请至少选择一条笔记');
      return;
    }

    const actionType = noteSelectionContext.operation === 'delete' ? 'DELETE_NOTE' : 'RESTORE_NOTE';
    const actions = Array.from(selectedNoteIds).map(noteId => ({
      type: actionType,
      noteId,
    }));

    ui.setShowNoteSelectionDialog(false);
    ui.setNoteSelectionContext(null);
    ui.setSelectedNoteIds(new Set());

    // 返回 actions JSON 供 executeAiAction 使用
    return JSON.stringify(actions);
  }, []);

  const handleCancelNoteSelection = useCallback(() => {
    ui.setShowNoteSelectionDialog(false);
    ui.setNoteSelectionContext(null);
    ui.setSelectedNoteIds(new Set());
    ui.setStatus('已取消操作');
  }, []);

  // ========== 智能分类对话框 ==========
  const handleCancelClassification = useCallback(() => {
    ui.setShowClassificationDialog(false);
    ui.setClassificationResult(null);
    ui.setSelectedClassifications(new Set());
    ui.setStatus('已取消分类');
  }, []);

  const handleToggleClassification = useCallback((noteId: string) => {
    ui.toggleClassification(noteId);
  }, []);

  const handleSelectAllClassifications = useCallback(() => {
    const result = useUiStore.getState().classificationResult;
    if (result) {
      ui.setSelectedClassifications(new Set(result.suggestions.map(s => s.noteId)));
    }
  }, []);

  const handleDeselectAllClassifications = useCallback(() => {
    ui.setSelectedClassifications(new Set());
  }, []);

  const handleConfirmClassification = useCallback(async () => {
    const { classificationResult, selectedClassifications } = useUiStore.getState();
    const { notes, folders } = useNoteStore.getState();
    if (!classificationResult) return;

    const selectedSuggestions = classificationResult.suggestions.filter(
      s => selectedClassifications.has(s.noteId)
    );

    if (selectedSuggestions.length === 0) {
      ui.setStatus('请至少选择一条笔记');
      return;
    }

    ui.setStatus('正在执行分类...');

    const existingFolderMap: Record<string, string> = {};
    folders.forEach(f => { existingFolderMap[f.name] = f.id; });

    const newFolderMap: Record<string, string> = {};
    const folderNamesToCreate = [...new Set(
      selectedSuggestions
        .filter(s => s.suggestedFolderName && !existingFolderMap[s.suggestedFolderName])
        .map(s => s.suggestedFolderName)
    )];

    for (const folderName of folderNamesToCreate) {
      try {
        const newFolder = await createFolder(folderName);
        newFolderMap[folderName] = newFolder.id;
      } catch (err) {
        console.error('创建文件夹失败:', folderName, err);
      }
    }

    let successCount = 0;
    for (const suggestion of selectedSuggestions) {
      try {
        const note = notes.find(n => n.id === suggestion.noteId);
        if (!note) continue;

        let targetFolderId: string | null = null;
        if (suggestion.suggestedFolderName) {
          targetFolderId = existingFolderMap[suggestion.suggestedFolderName] || null;
          if (!targetFolderId) {
            targetFolderId = newFolderMap[suggestion.suggestedFolderName] || null;
          }
        }
        if (!targetFolderId && suggestion.suggestedFolderId) {
          targetFolderId = suggestion.suggestedFolderId;
        }

        if (targetFolderId && targetFolderId !== note.folderId) {
          await updateNote(note.id, {
            title: note.title,
            content: note.content,
            tags: note.tags.map(t => t.name),
            folderId: targetFolderId,
          });
          successCount++;
        }
      } catch (err) {
        console.error('移动笔记失败:', suggestion.noteId, err);
      }
    }

    const [updatedNotes, updatedFolders] = await Promise.all([getNotes(), getFolders()]);
    noteStore.setNotes(updatedNotes);
    noteStore.setFolders(updatedFolders);

    ui.setShowClassificationDialog(false);
    ui.setClassificationResult(null);
    ui.setSelectedClassifications(new Set());
    ui.setStatus(`分类完成！已移动 ${successCount} 条笔记`);

    ai.addMessage({
      id: Date.now().toString(),
      content: `已完成分类，共移动 ${successCount} 条笔记~`,
      timestamp: Date.now(),
      role: 'spirit',
    });
  }, []);

  // ========== 拆分笔记对话框 ==========
  const handleCancelSplit = useCallback(() => {
    ui.setShowSplitDialog(false);
    ui.setSplitPreviews([]);
    ui.setSplitSourceNote(null);
    ui.setDeleteAfterSplit(false);
    ui.setStatus('已取消拆分');
  }, []);

  const handleToggleSplitItem = useCallback((index: number) => {
    ui.toggleSplitItem(index);
  }, []);

  const handleSelectAllSplit = useCallback(() => {
    const previews = useUiStore.getState().splitPreviews;
    ui.setSplitPreviews(previews.map(item => ({ ...item, selected: true })));
  }, []);

  const handleDeselectAllSplit = useCallback(() => {
    const previews = useUiStore.getState().splitPreviews;
    ui.setSplitPreviews(previews.map(item => ({ ...item, selected: false })));
  }, []);

  const handleConfirmSplit = useCallback(async () => {
    const { splitPreviews, splitSourceNote, deleteAfterSplit } = useUiStore.getState();
    const selectedItems = splitPreviews.filter(item => item.selected);
    if (selectedItems.length === 0) {
      ui.setStatus('请至少选择一条笔记');
      return;
    }

    ui.setStatus('正在创建笔记...');

    let successCount = 0;
    for (const item of selectedItems) {
      try {
        await createNote({
          title: item.title,
          content: item.content,
          tags: splitSourceNote?.tags.map(t => t.name) || [],
          folderId: splitSourceNote?.folderId || null,
        });
        successCount++;
      } catch (err) {
        console.error('创建笔记失败:', item.title, err);
      }
    }

    if (deleteAfterSplit && splitSourceNote) {
      try {
        await deleteNote(splitSourceNote.id);
      } catch (err) {
        console.error('删除原笔记失败:', err);
      }
    }

    const updatedNotes = await getNotes();
    noteStore.setNotes(updatedNotes);

    ui.setShowSplitDialog(false);
    ui.setSplitPreviews([]);
    ui.setSplitSourceNote(null);
    ui.setDeleteAfterSplit(false);
    ui.setStatus(`拆分完成！已创建 ${successCount} 条笔记`);

    ai.addMessage({
      id: Date.now().toString(),
      content: `已完成拆分，共创建 ${successCount} 条新笔记~`,
      timestamp: Date.now(),
      role: 'spirit',
    });
  }, []);

  // ========== 导出笔记对话框 ==========
  const handleCancelExport = useCallback(() => {
    ui.setShowExportDialog(false);
    ui.setExportCandidates([]);
    ui.setSelectedExportIds(new Set());
    ui.setStatus('已取消导出');
  }, []);

  const handleToggleExportItem = useCallback((noteId: string) => {
    ui.toggleExportItem(noteId);
  }, []);

  const handleSelectAllExport = useCallback(() => {
    const candidates = useUiStore.getState().exportCandidates;
    ui.setSelectedExportIds(new Set(candidates.map(n => n.id)));
  }, []);

  const handleDeselectAllExport = useCallback(() => {
    ui.setSelectedExportIds(new Set());
  }, []);

  const htmlToMarkdown = useCallback((html: string): string => {
    return html
      .replace(/<h1[^>]*>(.*?)<\/h1>/gi, '# $1\n\n')
      .replace(/<h2[^>]*>(.*?)<\/h2>/gi, '## $1\n\n')
      .replace(/<h3[^>]*>(.*?)<\/h3>/gi, '### $1\n\n')
      .replace(/<p[^>]*>(.*?)<\/p>/gi, '$1\n\n')
      .replace(/<br\s*\/?>/gi, '\n')
      .replace(/<strong[^>]*>(.*?)<\/strong>/gi, '**$1**')
      .replace(/<b[^>]*>(.*?)<\/b>/gi, '**$1**')
      .replace(/<em[^>]*>(.*?)<\/em>/gi, '*$1*')
      .replace(/<i[^>]*>(.*?)<\/i>/gi, '*$1*')
      .replace(/<code[^>]*>(.*?)<\/code>/gi, '`$1`')
      .replace(/<ul[^>]*>(.*?)<\/ul>/gis, '$1')
      .replace(/<ol[^>]*>(.*?)<\/ol>/gis, '$1')
      .replace(/<li[^>]*>(.*?)<\/li>/gi, '- $1\n')
      .replace(/<blockquote[^>]*>(.*?)<\/blockquote>/gis, '> $1\n')
      .replace(/<a[^>]*href="([^"]*)"[^>]*>(.*?)<\/a>/gi, '[$2]($1)')
      .replace(/<[^>]*>/g, '')
      .replace(/&nbsp;/g, ' ')
      .replace(/&lt;/g, '<')
      .replace(/&gt;/g, '>')
      .replace(/&amp;/g, '&')
      .replace(/\n{3,}/g, '\n\n')
      .trim();
  }, []);

  const handleConfirmExport = useCallback(async () => {
    const { exportCandidates, selectedExportIds } = useUiStore.getState();
    const selectedNotes = exportCandidates.filter(n => selectedExportIds.has(n.id));
    if (selectedNotes.length === 0) {
      ui.setStatus('请至少选择一条笔记');
      return;
    }

    ui.setStatus('正在导出...');

    try {
      if (selectedNotes.length === 1) {
        const note = selectedNotes[0];
        const mdContent = `# ${note.title}\n\n${htmlToMarkdown(note.content)}`;
        const blob = new Blob([mdContent], { type: 'text/markdown;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `${note.title.replace(/[<>:"/\\|?*]/g, '_')}.md`;
        a.click();
        URL.revokeObjectURL(url);
      } else {
        let combined = '';
        for (const note of selectedNotes) {
          combined += `# ${note.title}\n\n${htmlToMarkdown(note.content)}\n\n---\n\n`;
        }
        const blob = new Blob([combined], { type: 'text/markdown;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `导出笔记_${selectedNotes.length}条_${new Date().toISOString().split('T')[0]}.md`;
        a.click();
        URL.revokeObjectURL(url);
      }

      ui.setShowExportDialog(false);
      ui.setExportCandidates([]);
      ui.setSelectedExportIds(new Set());
      ui.setStatus(`导出成功！已导出 ${selectedNotes.length} 条笔记`);

      ai.addMessage({
        id: Date.now().toString(),
        content: `已导出 ${selectedNotes.length} 条笔记~`,
        timestamp: Date.now(),
        role: 'spirit',
      });
    } catch (err) {
      console.error('导出失败:', err);
      ui.setStatus('导出失败：' + (err as Error).message);
    }
  }, [htmlToMarkdown]);

  // ========== 摘要对话框 ==========
  const handleCancelSummary = useCallback(() => {
    ui.setShowSummaryDialog(false);
    ui.setSummaryContent('');
    ui.setSummaryTargetNote(null);
    ui.setStatus('已取消');
  }, []);

  const handleConfirmSummary = useCallback(async () => {
    const { summaryTargetNote, summaryContent } = useUiStore.getState();
    if (!summaryTargetNote || !summaryContent) return;

    ui.setStatus('正在更新笔记...');

    try {
      const summaryBlock = `<blockquote><strong>📝 摘要</strong><br/>${summaryContent}</blockquote><hr/>`;
      const newContent = summaryBlock + summaryTargetNote.content;

      await updateNote(summaryTargetNote.id, {
        title: summaryTargetNote.title,
        content: newContent,
        tags: summaryTargetNote.tags.map(t => t.name),
        folderId: summaryTargetNote.folderId,
      });

      const updatedNotes = await getNotes();
      noteStore.setNotes(updatedNotes);

      const { selectedNote } = useNoteStore.getState();
      if (selectedNote?.id === summaryTargetNote.id) {
        const updated = updatedNotes.find(n => n.id === summaryTargetNote.id);
        if (updated) {
          noteStore.setSelectedNote(updated);
        }
      }

      ui.setShowSummaryDialog(false);
      ui.setSummaryContent('');
      ui.setSummaryTargetNote(null);
      ui.setStatus('摘要已添加到笔记开头');

      ai.addMessage({
        id: Date.now().toString(),
        content: '已将摘要添加到笔记开头~',
        timestamp: Date.now(),
        role: 'spirit',
      });
    } catch (err) {
      console.error('更新笔记失败:', err);
      ui.setStatus('更新笔记失败：' + (err as Error).message);
    }
  }, []);

  return {
    // 笔记选择
    handleToggleNoteSelection,
    handleSelectAllNotes,
    handleDeselectAllNotes,
    handleConfirmNoteSelection,
    handleCancelNoteSelection,
    // 分类
    handleCancelClassification,
    handleToggleClassification,
    handleSelectAllClassifications,
    handleDeselectAllClassifications,
    handleConfirmClassification,
    // 拆分
    handleCancelSplit,
    handleToggleSplitItem,
    handleSelectAllSplit,
    handleDeselectAllSplit,
    handleConfirmSplit,
    // 导出
    handleCancelExport,
    handleToggleExportItem,
    handleSelectAllExport,
    handleDeselectAllExport,
    handleConfirmExport,
    // 摘要
    handleCancelSummary,
    handleConfirmSummary,
  };
}
