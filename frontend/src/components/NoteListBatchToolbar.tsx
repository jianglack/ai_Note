import { useState } from 'react';
import {
  TrashIcon,
  TagIcon,
  FolderIcon,
} from '@heroicons/react/24/outline';
import { useUiStore } from '../stores/uiStore';
import { useNoteStore } from '../stores/noteStore';
import { useToastStore } from '../stores/toastStore';
import {
  batchDeleteNotes,
  batchMoveNotes,
  batchAddTag,
  getNotes,
  getTrashNotes,
} from '../api';
import { askPrompt } from '../services/dialogService';

const T = {
  bg: '#fff',
  border: 'rgba(74,64,50,0.12)',
  text: 'var(--color-text, #2b2620)',
  textSecondary: 'var(--color-text-secondary, #7a6b58)',
  surfaceHover: 'var(--color-paper-2, #efe8d6)',
  accent: 'var(--color-accent, #b8452e)',
  shadow: '0 8px 24px rgba(74,64,50,0.12)',
};

export default function NoteListBatchToolbar() {
  const ui = useUiStore();
  const noteStore = useNoteStore();
  const toastStore = useToastStore();
  const [loading, setLoading] = useState(false);

  const selectedCount = ui.multiSelectedNoteIds.size;
  const selectedIds = Array.from(ui.multiSelectedNoteIds);

  const handleBatchAction = async (
    action: () => Promise<{ successCount: number; failedCount: number }>,
    label: string
  ) => {
    setLoading(true);
    try {
      const result = await action();
      if (result.failedCount === 0) {
        toastStore.addToast({ type: 'success', title: `${result.successCount} 篇笔记已${label}` });
      } else {
        toastStore.addToast({ type: 'partial', title: `成功 ${result.successCount}，失败 ${result.failedCount}` });
      }
      const [notes, trash] = await Promise.all([getNotes(), getTrashNotes()]);
      noteStore.setNotes(notes);
      noteStore.setTrashNotes(trash);
      ui.setMultiSelectMode(false);
    } catch (err) {
      toastStore.addToast({ type: 'failed', title: `${label}失败`, message: (err as Error).message });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      position: 'absolute', bottom: 12, left: 12, right: 12,
      background: T.bg, border: `1px solid ${T.border}`, borderRadius: 16,
      boxShadow: T.shadow, padding: '8px 10px',
      display: 'flex', alignItems: 'center', gap: 6,
    }}>
      {/* Count pill */}
      <span style={{
        background: T.accent, color: '#fff', fontSize: 12, fontWeight: 500,
        borderRadius: 20, padding: '2px 8px', flexShrink: 0,
      }}>
        {selectedCount} 篇
      </span>

      <div style={{ flex: 1 }} />

      {/* Action buttons */}
      <ActionBtn icon={<TrashIcon style={{ width: 18, height: 18 }} />} label="删除" disabled={loading}
        onClick={() => handleBatchAction(() => batchDeleteNotes(selectedIds), '删除')} />
      <ActionBtn icon={<FolderIcon style={{ width: 18, height: 18 }} />} label="移动" disabled={loading}
        onClick={() => {
          const folders = noteStore.folders;
          if (folders.length === 0) {
            toastStore.addToast({ type: 'info', title: '暂无文件夹' });
            return;
          }
          const folderNames = folders.map((f, i) => `${i + 1}. ${f.name}`).join('\n');
          void askPrompt({
            title: 'Move notes',
            message: `Choose target folder by number:\n${folderNames}`,
            confirmLabel: 'Move',
          }).then((choice) => {
            if (choice) {
              const idx = parseInt(choice) - 1;
              if (idx >= 0 && idx < folders.length) {
                handleBatchAction(() => batchMoveNotes(selectedIds, folders[idx].id), '移动');
              }
            }
          });
        }} />
      <ActionBtn icon={<TagIcon style={{ width: 18, height: 18 }} />} label="标签" disabled={loading}
        onClick={() => {
          void askPrompt({
            title: 'Add tag',
            message: 'Tag name',
            confirmLabel: 'Add',
          }).then((tag) => {
            if (tag) handleBatchAction(() => batchAddTag(selectedIds, tag), '添加标签');
          });
        }} />
    </div>
  );
}

function ActionBtn({ icon, label, disabled, onClick }: {
  icon: React.ReactNode; label: string; disabled?: boolean; onClick: () => void;
}) {
  return (
    <button
      onClick={onClick} disabled={disabled} title={label}
      style={{
        display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2,
        padding: '6px 8px', borderRadius: 10, border: 'none',
        background: 'transparent', cursor: disabled ? 'not-allowed' : 'pointer',
        opacity: disabled ? 0.4 : 1, transition: 'background 0.15s',
        color: T.textSecondary, fontFamily: 'inherit',
      }}
      onMouseEnter={e => { if (!disabled) e.currentTarget.style.background = T.surfaceHover; }}
      onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
    >
      {icon}
      <span style={{ fontSize: 11 }}>{label}</span>
    </button>
  );
}
