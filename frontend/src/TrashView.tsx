import { useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { markdownUrlTransform } from './security/contentSafety';
import { askConfirm } from './services/dialogService';
import type { Note } from './api';

interface Props {
  notes: Note[];
  onClose: () => void;
  onRestore: (id: string) => void;
  onPermanentDelete: (id: string) => void;
}

export default function TrashView({ notes, onRestore, onPermanentDelete, onClose }: Props) {
  const handleDelete = async (id: string, title: string) => {
    if (await askConfirm({
      title: 'Permanent delete',
      message: `Delete "${title}" permanently? This cannot be undone.`,
      confirmLabel: 'Delete',
      danger: true,
    })) {
      onPermanentDelete(id);
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

  return (
    <div className="trash-view">
      <div className="trash-header">
        <h2>🗑️ 回收站</h2>
        <button type="button" className="close-button" aria-label="Close trash" onClick={onClose}>
          ✕
        </button>
      </div>

      <div className="trash-body">
        {notes.length === 0 ? (
          <div className="trash-empty">
            <p>回收站是空的</p>
          </div>
        ) : (
          <div className="trash-list">
            {notes.map((note) => (
              <div key={note.id} className="trash-item">
                <div className="trash-item-header">
                  <h3 className="trash-item-title">{note.title || '无标题'}</h3>
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
                    📁 原文件夹 ID: {note.folderId}
                  </div>
                )}

                <div className="trash-item-preview">
                  <ReactMarkdown remarkPlugins={[remarkGfm]} urlTransform={markdownUrlTransform}>
                    {note.content || '无内容'}
                  </ReactMarkdown>
                </div>

                <div className="trash-item-actions">
                  <button
                    className="restore-button"
                    onClick={() => onRestore(note.id)}
                  >
                    ↩️ 恢复
                  </button>
                  <button
                    className="delete-button"
                    onClick={() => handleDelete(note.id, note.title)}
                  >
                    🗑️ 永久删除
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
