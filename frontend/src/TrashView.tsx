import { useMemo, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import type { Note } from './api';
import { ToolDetailPanel, ToolWorkbenchShell } from './components/workbench';
import { markdownUrlTransform } from './security/contentSafety';
import { askConfirm } from './services/dialogService';

interface Props {
  notes: Note[];
  onClose: () => void;
  onRestore: (id: string) => void;
  onPermanentDelete: (id: string) => void;
}

const T = {
  bg: 'var(--color-paper-0, #fbf7ee)',
  surface: 'var(--color-paper-1, #f5efe0)',
  border: 'rgba(74,64,50,0.12)',
  borderStrong: 'rgba(74,64,50,0.22)',
  text: 'var(--color-text, #2b2620)',
  textSecondary: 'var(--color-text-secondary, #7a6b58)',
  font: '-apple-system, "PingFang SC", "Noto Sans SC", "Helvetica Neue", sans-serif',
};

export default function TrashView({ notes, onRestore, onPermanentDelete, onClose }: Props) {
  const [selectedNoteId, setSelectedNoteId] = useState<string | null>(null);

  const selectedNote = useMemo(
    () => notes.find(note => note.id === selectedNoteId) ?? null,
    [notes, selectedNoteId],
  );

  const getTitle = (note: Note | null) => note?.title || '无标题';

  const handleRestore = (note: Note) => {
    onRestore(note.id);
  };

  const handleDelete = async (note: Note) => {
    if (await askConfirm({
      title: 'Permanent delete',
      message: `Delete "${getTitle(note)}" permanently? This cannot be undone.`,
      confirmLabel: 'Delete',
      danger: true,
    })) {
      onPermanentDelete(note.id);
    }
  };

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  const detailActions = selectedNote ? (
    <button
      type="button"
      className="restore-button"
      aria-label={`恢复笔记 ${getTitle(selectedNote)}`}
      onClick={() => handleRestore(selectedNote)}
    >
      恢复
    </button>
  ) : undefined;

  const dangerActions = selectedNote ? (
    <button
      type="button"
      className="delete-button"
      aria-label={`永久删除笔记 ${getTitle(selectedNote)}`}
      onClick={() => handleDelete(selectedNote)}
    >
      永久删除
    </button>
  ) : undefined;

  return (
    <ToolWorkbenchShell
      title="回收站"
      subtitle={`${notes.length} 条已删除笔记`}
      onBack={onClose}
      backLabel="关闭回收站"
      closeOnEscape
      detailPanel={(
        <ToolDetailPanel
          title="笔记预览"
          subtitle={selectedNote ? getTitle(selectedNote) : undefined}
          meta={selectedNote ? `删除时间: ${formatDate(selectedNote.updatedAt)}` : undefined}
          empty={!selectedNote}
          emptyMessage="选择一条回收站笔记查看内容。"
          actions={detailActions}
          dangerActions={dangerActions}
        >
          {selectedNote && (
            <div style={{
              padding: 12,
              border: `1px solid ${T.border}`,
              borderRadius: 8,
              background: T.bg,
              color: T.text,
              fontSize: 14,
              lineHeight: 1.65,
            }}>
              <ReactMarkdown remarkPlugins={[remarkGfm]} urlTransform={markdownUrlTransform}>
                {selectedNote.content || '无内容'}
              </ReactMarkdown>
            </div>
          )}
        </ToolDetailPanel>
      )}
    >
      <div style={{
        height: '100%',
        overflowY: 'auto',
        padding: 24,
        boxSizing: 'border-box',
        background: T.bg,
        fontFamily: T.font,
      }}>
        {notes.length === 0 ? (
          <div className="trash-empty">
            <p>回收站是空的</p>
          </div>
        ) : (
          <div className="trash-list" style={{ maxWidth: 920 }}>
            {notes.map((note) => {
              const title = getTitle(note);
              const isSelected = selectedNoteId === note.id;

              return (
                <button
                  key={note.id}
                  type="button"
                  className="trash-item"
                  aria-label={`选择回收站笔记 ${title}`}
                  aria-pressed={isSelected}
                  onClick={() => setSelectedNoteId(note.id)}
                  style={{
                    width: '100%',
                    textAlign: 'left',
                    cursor: 'pointer',
                    fontFamily: T.font,
                    background: isSelected ? T.surface : T.bg,
                    border: isSelected ? `1px solid ${T.borderStrong}` : `1px solid ${T.border}`,
                  }}
                >
                  <div className="trash-item-header">
                    <h3 className="trash-item-title">{title}</h3>
                    <div className="trash-item-meta">
                      <span className="trash-item-date">
                        删除时间: {formatDate(note.updatedAt)}
                      </span>
                    </div>
                  </div>

                  {note.tags && note.tags.length > 0 && (
                    <div className="trash-item-tags">
                      {note.tags.map((tag) => (
                        <span key={tag.id} className="tag">
                          {tag.name}
                        </span>
                      ))}
                    </div>
                  )}

                  {note.folderId && (
                    <div className="trash-item-folder">
                      原文件夹 ID: {note.folderId}
                    </div>
                  )}

                  <div style={{
                    color: T.textSecondary,
                    fontSize: 13,
                    lineHeight: 1.55,
                    overflow: 'hidden',
                    display: '-webkit-box',
                    WebkitLineClamp: 2,
                    WebkitBoxOrient: 'vertical',
                  }}>
                    {note.content || '无内容'}
                  </div>
                </button>
              );
            })}
          </div>
        )}
      </div>
    </ToolWorkbenchShell>
  );
}
