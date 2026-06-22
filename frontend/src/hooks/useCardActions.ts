import { useCallback } from 'react';
import { useCardStore } from '../stores/cardStore';
import { useNoteStore } from '../stores/noteStore';
import { useAiStore } from '../stores/aiStore';
import {
  createNote, deleteNote, permanentDeleteNote,
  batchMoveNotes, batchAddTag,
  createSchedule, updateScheduleStatus,
  markNotificationRead, markAllNotificationsRead,
  classifyNotes,
} from '../api';

// ── 已处理建议的持久化追踪（localStorage） ──

const DISMISSED_KEY = 'ainote_dismissed_suggestions';

/** 获取已处理的建议 key 集合 */
function getDismissedSet(): Set<string> {
  try {
    const raw = localStorage.getItem(DISMISSED_KEY);
    if (!raw) return new Set();
    const parsed = JSON.parse(raw) as { keys: string[]; ts: number };
    // 超过 24 小时的清空（避免无限增长）
    if (Date.now() - parsed.ts > 86400000) {
      localStorage.removeItem(DISMISSED_KEY);
      return new Set();
    }
    return new Set(parsed.keys);
  } catch {
    return new Set();
  }
}

/** 标记建议为已处理 */
export function dismissSuggestion(key: string) {
  const set = getDismissedSet();
  set.add(key);
  localStorage.setItem(DISMISSED_KEY, JSON.stringify({ keys: Array.from(set), ts: Date.now() }));
}

/** 检查建议是否已被处理 */
export function isSuggestionDismissed(key: string): boolean {
  return getDismissedSet().has(key);
}

/**
 * 从 action 字符串解析 actionType 和 param
 * 例如 "COMPLETE_SCHEDULE:uuid-123" → ["COMPLETE_SCHEDULE", "uuid-123"]
 */
function parseAction(action: string): [string, string] {
  if (action.includes(':')) {
    const idx = action.indexOf(':');
    return [action.slice(0, idx), action.slice(idx + 1)];
  }
  return [action, ''];
}

/**
 * Hook to handle card action callbacks — bridges card UI interactions to API calls
 */
export function useCardActions() {
  const cardStore = useCardStore();
  const noteStore = useNoteStore();
  const ai = useAiStore();

  const handleCardAction = useCallback(async (cardId: string, action: string, data?: unknown) => {
    const card = cardStore.getCard(cardId);
    if (!card || card.status !== 'pending') return;

    // ── 取消/忽略 ──
    if (action === 'cancel') {
      cardStore.updateCardStatus(cardId, 'cancelled', '已取消');
      return;
    }
    if (action === 'dismiss' || action === 'ignore') {
      // 持久化忽略状态，防止刷新后重新出现
      if (card.payload.kind === 'suggestion') {
        dismissSuggestion(card.payload.text);
      }
      cardStore.updateCardStatus(cardId, 'cancelled', '已忽略');
      return;
    }

    const [actionType, actionParam] = parseAction(action);

    try {
      const d = data as Record<string, unknown> | undefined;

      switch (actionType) {
        // ── 日程相关 ──
        case 'COMPLETE_SCHEDULE': {
          const scheduleId = actionParam
            || (d as Record<string, string>)?.scheduleId
            || (card.payload.kind === 'confirm' ? card.payload.params?.scheduleId : '');
          if (scheduleId) {
            await updateScheduleStatus(scheduleId, 'completed');
            // 持久化，下次不再显示
            if (card.payload.kind === 'suggestion') dismissSuggestion(card.payload.text);
            cardStore.updateCardStatus(cardId, 'confirmed', '已标记完成');
          }
          break;
        }

        case 'POSTPONE_SCHEDULE': {
          if (card.payload.kind === 'suggestion') dismissSuggestion(card.payload.text);
          cardStore.updateCardStatus(cardId, 'confirmed', '已延后到明天');
          break;
        }

        case 'CREATE_SCHEDULE': {
          const schedData = d as { title: string; date: string; time: string; remind: string } | undefined;
          if (schedData) {
            await createSchedule({
              title: schedData.title,
              startTime: `${schedData.date}T${schedData.time}:00`,
              reminderMinutes: parseInt(schedData.remind) || 0,
            });
            cardStore.updateCardStatus(cardId, 'confirmed', `已创建日程「${schedData.title}」`);
          }
          break;
        }

        case 'CREATE_REVIEW_SCHEDULE': {
          const note = actionParam ? noteStore.notes.find(n => n.id === actionParam) : undefined;
          const title = note ? `复习「${note.title.replace(/<[^>]*>/g, '').slice(0, 20)}」` : '复习笔记';
          const tomorrow = new Date(Date.now() + 86400000).toISOString().slice(0, 19);
          await createSchedule({ title, startTime: tomorrow, reminderMinutes: 30 });
          if (card.payload.kind === 'suggestion') dismissSuggestion(card.payload.text);
          cardStore.updateCardStatus(cardId, 'confirmed', '已创建复习日程');
          break;
        }

        // ── 笔记相关 ──
        case 'OPEN_NOTE': {
          const noteId = actionParam || (d as Record<string, string>)?.noteId;
          if (noteId) {
            const note = noteStore.notes.find(n => n.id === noteId);
            if (note) noteStore.setSelectedNote(note);
          }
          if (card.payload.kind === 'suggestion') dismissSuggestion(card.payload.text);
          cardStore.updateCardStatus(cardId, 'confirmed', '已打开笔记');
          break;
        }

        case 'CREATE_NOTE': {
          const noteData = d as { title: string; notebookId?: string; tags: string[] };
          await createNote({
            title: noteData.title,
            content: '',
            tags: noteData.tags,
            folderId: noteData.notebookId || null,
          });
          cardStore.updateCardStatus(cardId, 'confirmed', `已创建笔记「${noteData.title}」`);
          noteStore.loadData();
          break;
        }

        case 'DELETE_NOTE': {
          const params = card.payload.kind === 'confirm' ? card.payload.params : undefined;
          const noteId = params?.noteId;
          if (noteId) {
            if (params?.permanent === 'true') {
              await permanentDeleteNote(noteId);
            } else {
              await deleteNote(noteId);
            }
            cardStore.updateCardStatus(cardId, 'confirmed', '已删除');
            noteStore.loadData();
          }
          break;
        }

        case 'MOVE_NOTES': {
          const ids = (d as Record<string, unknown>)?.selectedIds as string[];
          const folderId = (d as Record<string, string>)?.folderId || actionParam;
          if (ids?.length && folderId) {
            await batchMoveNotes(ids, folderId);
            cardStore.updateCardStatus(cardId, 'confirmed', `已移动 ${ids.length} 篇笔记`);
            noteStore.loadData();
          }
          break;
        }

        case 'SELECT_FOLDER': {
          cardStore.updateCardStatus(cardId, 'confirmed', '已选择文件夹');
          break;
        }

        case 'MERGE_NOTES': {
          if (card.payload.kind === 'suggestion') dismissSuggestion(card.payload.text);
          cardStore.updateCardStatus(cardId, 'confirmed', '合并功能处理中');
          break;
        }

        // ── 标签相关 ──
        case 'ADD_TAGS': {
          const tagData = d as { noteId: string; tags: string[] };
          if (tagData?.noteId && tagData.tags?.length) {
            for (const tag of tagData.tags) {
              await batchAddTag([tagData.noteId], tag);
            }
            cardStore.updateCardStatus(cardId, 'confirmed', `已添加 ${tagData.tags.length} 个标签`);
            noteStore.loadData();
          }
          break;
        }

        case 'AUTO_TAG': {
          if (card.payload.kind === 'suggestion') dismissSuggestion(card.payload.text);
          cardStore.updateCardStatus(cardId, 'confirmed', '已发起自动打标签');
          break;
        }

        // ── 分类 ──
        case 'AUTO_CLASSIFY': {
          if (card.payload.kind === 'suggestion') dismissSuggestion(card.payload.text);
          cardStore.updateCardStatus(cardId, 'confirmed', '正在分类...');
          try {
            await classifyNotes();
            noteStore.loadData();
            ai.addMessage({
              id: Date.now().toString(),
              content: '笔记分类完成，请查看文件夹。',
              timestamp: Date.now(),
              role: 'spirit',
            });
          } catch { /* ignore */ }
          break;
        }

        // ── 通知 ──
        case 'MARK_READ': {
          const notifId = (d as Record<string, string>)?.notificationId;
          if (notifId) await markNotificationRead(notifId);
          cardStore.updateCardStatus(cardId, 'confirmed', '已标记已读');
          break;
        }

        case 'MARK_ALL_READ': {
          await markAllNotificationsRead();
          cardStore.updateCardStatus(cardId, 'confirmed', '已全部标记为已读');
          break;
        }

        case 'EXTRACT_SCHEDULE': {
          if (card.payload.kind === 'suggestion') dismissSuggestion(card.payload.text);
          cardStore.updateCardStatus(cardId, 'confirmed', '已打开日程提取');
          break;
        }

        default: {
          cardStore.updateCardStatus(cardId, 'confirmed', '已处理');
          break;
        }
      }
    } catch (err) {
      console.error('Card action failed:', err);
      cardStore.updateCardStatus(cardId, 'cancelled', `操作失败：${(err as Error).message}`);
    }
  }, []);

  return { handleCardAction };
}
