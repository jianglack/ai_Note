import { useCallback, useEffect, useRef, type KeyboardEvent } from 'react';
import { XMarkIcon } from '@heroicons/react/24/outline';
import { useUiStore } from '../stores/uiStore';
import { useActions } from '../hooks/useActions';
import { useDialogs } from '../hooks/useDialogs';

export default function ConfirmDialogs() {
  const ui = useUiStore();
  const { handleConfirmActions, handleCancelActions } = useActions();
  const {
    handleToggleNoteSelection, handleSelectAllNotes, handleDeselectAllNotes,
    handleConfirmNoteSelection, handleCancelNoteSelection,
    handleCancelClassification, handleToggleClassification,
    handleSelectAllClassifications, handleDeselectAllClassifications, handleConfirmClassification,
    handleCancelSplit, handleToggleSplitItem, handleSelectAllSplit, handleDeselectAllSplit, handleConfirmSplit,
    handleCancelExport, handleToggleExportItem, handleSelectAllExport, handleDeselectAllExport, handleConfirmExport,
    handleCancelSummary, handleConfirmSummary,
  } = useDialogs();

  const dialogPanelRef = useRef<HTMLDivElement | null>(null);
  const previousFocusRef = useRef<HTMLElement | null>(null);
  const isAnyDialogOpen = Boolean(
    ui.showConfirmDialog ||
    ui.showNoteSelectionDialog ||
    ui.showClassificationDialog ||
    ui.showSplitDialog ||
    ui.showExportDialog ||
    ui.showSummaryDialog
  );

  useEffect(() => {
    if (!isAnyDialogOpen) return;
    previousFocusRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    dialogPanelRef.current?.focus();

    return () => {
      previousFocusRef.current?.focus();
      previousFocusRef.current = null;
    };
  }, [isAnyDialogOpen]);

  const handleDialogKeyDown = useCallback((event: KeyboardEvent<HTMLDivElement>, onClose: () => void) => {
    if (event.key === 'Escape') {
      event.preventDefault();
      onClose();
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
  }, []);

  return (
    <>
      {/* 操作确认对话框 */}
      {ui.showConfirmDialog && (
        <div className="dialog-overlay">
          <div
            className="dialog-panel"
            role="dialog"
            aria-modal="true"
            aria-labelledby="confirm-actions-title"
            ref={dialogPanelRef}
            tabIndex={-1}
            onKeyDown={(event) => handleDialogKeyDown(event, handleCancelActions)}
          >
            <div className="dialog-header">
              <h3 id="confirm-actions-title">确认操作</h3>
              <button type="button" className="dialog-close" aria-label="Close action confirmation dialog" onClick={handleCancelActions}>
                <XMarkIcon className="w-5 h-5" />
              </button>
            </div>
            <div className="dialog-body">
              <p className="mb-lg text-sm text-text-secondary">
                AI 将执行以下 {ui.pendingActions.length} 个操作：
              </p>
              <div className="flex flex-col gap-sm max-h-[400px] overflow-y-auto">
                {ui.pendingActions.map((action) => (
                  <div key={action.id} className="flex items-center gap-md px-lg py-md bg-bg border border-border rounded-md transition-colors hover:bg-border/30">
                    <span className="text-xl shrink-0">
                      {action.type === 'DELETE_NOTE' && '🗑️'}
                      {action.type === 'RESTORE_NOTE' && '♻️'}
                      {action.type === 'CREATE_NOTE' && '📝'}
                      {action.type === 'UPDATE_NOTE' && '✏️'}
                      {action.type === 'ADD_TAG' && '🏷️'}
                      {action.type === 'REMOVE_TAG' && '🚫'}
                      {action.type === 'CREATE_SCHEDULE' && '📅'}
                      {action.type === 'CREATE_SCHEDULES_BATCH' && '📅'}
                    </span>
                    <span className="text-sm text-text flex-1">{action.description}</span>
                  </div>
                ))}
              </div>
            </div>
            <div className="dialog-footer">
              <button className="dialog-btn dialog-btn-cancel" onClick={handleCancelActions}>取消</button>
              <button className="dialog-btn dialog-btn-confirm" onClick={handleConfirmActions}>确认执行</button>
            </div>
          </div>
        </div>
      )}

      {/* 笔记选择对话框 */}
      {ui.showNoteSelectionDialog && ui.noteSelectionContext && (
        <div className="dialog-overlay">
          <div
            className="dialog-panel"
            role="dialog"
            aria-modal="true"
            aria-labelledby="note-selection-title"
            ref={dialogPanelRef}
            tabIndex={-1}
            onKeyDown={(event) => handleDialogKeyDown(event, handleCancelNoteSelection)}
          >
            <div className="dialog-header">
              <h3 id="note-selection-title">选择笔记</h3>
              <button type="button" className="dialog-close" aria-label="Close note selection dialog" onClick={handleCancelNoteSelection}>
                <XMarkIcon className="w-5 h-5" />
              </button>
            </div>
            <div className="dialog-body">
              <p className="mb-lg text-sm text-text-secondary">{ui.noteSelectionContext.message}</p>
              <div className="dialog-select-actions">
                <button className="dialog-select-btn" onClick={handleSelectAllNotes}>全选</button>
                <button className="dialog-select-btn" onClick={handleDeselectAllNotes}>取消全选</button>
                <span className="dialog-select-count">
                  已选择 {ui.selectedNoteIds.size} / {ui.noteSelectionContext.candidates.length} 条
                </span>
              </div>
              <div className="max-h-[400px] overflow-y-auto">
                {ui.noteSelectionContext.candidates.map((note) => (
                  <label
                    key={note.id}
                    className={`dialog-list-item ${ui.selectedNoteIds.has(note.id) ? 'selected' : ''}`}
                  >
                    <input
                      type="checkbox"
                      checked={ui.selectedNoteIds.has(note.id)}
                      onChange={() => handleToggleNoteSelection(note.id)}
                    />
                    <div className="flex-1 min-w-0">
                      <div className="text-sm font-semibold text-text truncate">{note.title || '无标题'}</div>
                      <div className="text-xs text-text-secondary mt-xs line-clamp-2">{note.content.slice(0, 100)}...</div>
                      {note.tags && note.tags.length > 0 && (
                        <div className="flex flex-wrap gap-xs mt-xs">
                          {note.tags.map((tag) => (
                            <span key={tag.id} className="text-[10px] px-[6px] py-[1px] bg-tag-bg text-tag-text rounded-sm">{tag.name}</span>
                          ))}
                        </div>
                      )}
                    </div>
                  </label>
                ))}
              </div>
            </div>
            <div className="dialog-footer">
              <button className="dialog-btn dialog-btn-cancel" onClick={handleCancelNoteSelection}>取消</button>
              <button
                className="dialog-btn dialog-btn-confirm"
                onClick={handleConfirmNoteSelection}
                disabled={ui.selectedNoteIds.size === 0}
              >
                确认{ui.noteSelectionContext!.operation === 'delete' ? '删除' : '恢复'} ({ui.selectedNoteIds.size})
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 智能分类对话框 */}
      {ui.showClassificationDialog && ui.classificationResult && (
        <div className="dialog-overlay">
          <div
            className="dialog-panel"
            role="dialog"
            aria-modal="true"
            aria-labelledby="classification-title"
            ref={dialogPanelRef}
            tabIndex={-1}
            onKeyDown={(event) => handleDialogKeyDown(event, handleCancelClassification)}
          >
            <div className="dialog-header">
              <h3 id="classification-title">智能分类建议</h3>
              <button type="button" className="dialog-close" aria-label="Close classification dialog" onClick={handleCancelClassification}>
                <XMarkIcon className="w-5 h-5" />
              </button>
            </div>
            <div className="dialog-body">
              <p className="mb-md text-sm text-text-secondary">{ui.classificationResult.summary}</p>
              {ui.classificationResult.newFolders.length > 0 && (
                <div className="mb-md px-md py-sm bg-accent/10 text-accent text-xs rounded-md">
                  将新建文件夹：{ui.classificationResult.newFolders.join('、')}
                </div>
              )}
              <div className="dialog-select-actions">
                <button className="dialog-select-btn" onClick={handleSelectAllClassifications}>全选</button>
                <button className="dialog-select-btn" onClick={handleDeselectAllClassifications}>取消全选</button>
                <span className="dialog-select-count">
                  已选择 {ui.selectedClassifications.size} / {ui.classificationResult!.suggestions.length} 条
                </span>
              </div>
              <div className="max-h-[400px] overflow-y-auto">
                {ui.classificationResult!.suggestions.map((suggestion) => (
                  <label
                    key={suggestion.noteId}
                    className={`dialog-list-item ${ui.selectedClassifications.has(suggestion.noteId) ? 'selected' : ''}`}
                  >
                    <input
                      type="checkbox"
                      checked={ui.selectedClassifications.has(suggestion.noteId)}
                      onChange={() => handleToggleClassification(suggestion.noteId)}
                    />
                    <div className="flex-1 min-w-0">
                      <div className="text-sm font-semibold text-text">{suggestion.noteTitle || '无标题'}</div>
                      <div className="text-xs text-accent mt-xs">
                        → <span className={suggestion.isNewFolder ? 'font-semibold' : ''}>
                          {suggestion.suggestedFolderName}
                          {suggestion.isNewFolder && ' (新建)'}
                        </span>
                      </div>
                      <div className="text-xs text-text-secondary mt-xs">{suggestion.reason}</div>
                    </div>
                  </label>
                ))}
              </div>
            </div>
            <div className="dialog-footer">
              <button className="dialog-btn dialog-btn-cancel" onClick={handleCancelClassification}>取消</button>
              <button
                className="dialog-btn dialog-btn-confirm"
                onClick={handleConfirmClassification}
                disabled={ui.selectedClassifications.size === 0}
              >
                确认分类 ({ui.selectedClassifications.size})
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 拆分笔记对话框 */}
      {ui.showSplitDialog && ui.splitPreviews.length > 0 && (
        <div className="dialog-overlay">
          <div
            className="dialog-panel"
            role="dialog"
            aria-modal="true"
            aria-labelledby="split-preview-title"
            ref={dialogPanelRef}
            tabIndex={-1}
            onKeyDown={(event) => handleDialogKeyDown(event, handleCancelSplit)}
          >
            <div className="dialog-header">
              <h3 id="split-preview-title">拆分笔记预览</h3>
              <button type="button" className="dialog-close" aria-label="Close split preview dialog" onClick={handleCancelSplit}>
                <XMarkIcon className="w-5 h-5" />
              </button>
            </div>
            <div className="dialog-body">
              <p className="mb-lg text-sm text-text-secondary">
                原笔记「{ui.splitSourceNote?.title}」将拆分为 {ui.splitPreviews.length} 条新笔记
              </p>
              <div className="dialog-select-actions">
                <button className="dialog-select-btn" onClick={handleSelectAllSplit}>全选</button>
                <button className="dialog-select-btn" onClick={handleDeselectAllSplit}>取消全选</button>
                <span className="dialog-select-count">
                  已选择 {ui.splitPreviews.filter(p => p.selected).length} / {ui.splitPreviews.length} 条
                </span>
              </div>
              <div className="max-h-[400px] overflow-y-auto">
                {ui.splitPreviews.map((preview, index) => (
                  <label
                    key={index}
                    className={`dialog-list-item ${preview.selected ? 'selected' : ''}`}
                  >
                    <input
                      type="checkbox"
                      checked={preview.selected}
                      onChange={() => handleToggleSplitItem(index)}
                    />
                    <div className="flex-1 min-w-0">
                      <div className="text-sm font-semibold text-text">{preview.title}</div>
                      <div className="text-xs text-text-secondary mt-xs line-clamp-3">{preview.content.substring(0, 150)}...</div>
                    </div>
                  </label>
                ))}
              </div>
              <div className="mt-lg pt-md border-t border-border">
                <label className="flex items-center gap-sm text-sm text-text-secondary cursor-pointer">
                  <input
                    type="checkbox"
                    className="accent-danger"
                    checked={ui.deleteAfterSplit}
                    onChange={(e) => ui.setDeleteAfterSplit(e.target.checked)}
                  />
                  拆分后删除原笔记
                </label>
              </div>
            </div>
            <div className="dialog-footer">
              <button className="dialog-btn dialog-btn-cancel" onClick={handleCancelSplit}>取消</button>
              <button
                className="dialog-btn dialog-btn-confirm"
                onClick={handleConfirmSplit}
                disabled={ui.splitPreviews.filter(p => p.selected).length === 0}
              >
                确认拆分 ({ui.splitPreviews.filter(p => p.selected).length})
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 导出笔记对话框 */}
      {ui.showExportDialog && ui.exportCandidates.length > 0 && (
        <div className="dialog-overlay">
          <div
            className="dialog-panel"
            role="dialog"
            aria-modal="true"
            aria-labelledby="export-notes-title"
            ref={dialogPanelRef}
            tabIndex={-1}
            onKeyDown={(event) => handleDialogKeyDown(event, handleCancelExport)}
          >
            <div className="dialog-header">
              <h3 id="export-notes-title">导出笔记</h3>
              <button type="button" className="dialog-close" aria-label="Close export dialog" onClick={handleCancelExport}>
                <XMarkIcon className="w-5 h-5" />
              </button>
            </div>
            <div className="dialog-body">
              <p className="mb-lg text-sm text-text-secondary">选择要导出的笔记（将导出为 Markdown 格式）</p>
              <div className="dialog-select-actions">
                <button className="dialog-select-btn" onClick={handleSelectAllExport}>全选</button>
                <button className="dialog-select-btn" onClick={handleDeselectAllExport}>取消全选</button>
                <span className="dialog-select-count">
                  已选择 {ui.selectedExportIds.size} / {ui.exportCandidates.length} 条
                </span>
              </div>
              <div className="max-h-[400px] overflow-y-auto">
                {ui.exportCandidates.map((note) => (
                  <label
                    key={note.id}
                    className={`dialog-list-item ${ui.selectedExportIds.has(note.id) ? 'selected' : ''}`}
                  >
                    <input
                      type="checkbox"
                      checked={ui.selectedExportIds.has(note.id)}
                      onChange={() => handleToggleExportItem(note.id)}
                    />
                    <div className="flex-1 min-w-0">
                      <div className="text-sm font-semibold text-text">{note.title || '无标题'}</div>
                      <div className="text-xs text-text-secondary mt-xs line-clamp-2">
                        {note.content.replace(/<[^>]*>/g, '').substring(0, 100)}...
                      </div>
                    </div>
                  </label>
                ))}
              </div>
            </div>
            <div className="dialog-footer">
              <button className="dialog-btn dialog-btn-cancel" onClick={handleCancelExport}>取消</button>
              <button
                className="dialog-btn dialog-btn-confirm"
                onClick={handleConfirmExport}
                disabled={ui.selectedExportIds.size === 0}
              >
                导出 ({ui.selectedExportIds.size})
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 摘要预览对话框 */}
      {ui.showSummaryDialog && ui.summaryContent && (
        <div className="dialog-overlay">
          <div
            className="dialog-panel"
            role="dialog"
            aria-modal="true"
            aria-labelledby="summary-preview-title"
            ref={dialogPanelRef}
            tabIndex={-1}
            onKeyDown={(event) => handleDialogKeyDown(event, handleCancelSummary)}
          >
            <div className="dialog-header">
              <h3 id="summary-preview-title">摘要预览</h3>
              <button type="button" className="dialog-close" aria-label="Close summary dialog" onClick={handleCancelSummary}>
                <XMarkIcon className="w-5 h-5" />
              </button>
            </div>
            <div className="dialog-body">
              <p className="mb-md text-sm text-text-secondary">
                为「{ui.summaryTargetNote?.title}」生成的摘要：
              </p>
              <div className="p-lg bg-bg border border-border rounded-md text-sm text-text leading-relaxed whitespace-pre-wrap">
                {ui.summaryContent}
              </div>
              <p className="mt-md text-xs text-text-secondary italic">确认后将把摘要插入笔记开头</p>
            </div>
            <div className="dialog-footer">
              <button className="dialog-btn dialog-btn-cancel" onClick={handleCancelSummary}>取消</button>
              <button className="dialog-btn dialog-btn-confirm" onClick={handleConfirmSummary}>确认添加</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
