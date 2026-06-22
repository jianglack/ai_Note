import { useCallback } from 'react';
import { useNoteStore } from '../stores/noteStore';
import { useUiStore } from '../stores/uiStore';
import { useAiStore } from '../stores/aiStore';
import { useScheduleStore } from '../stores/scheduleStore';
import {
  createNote, updateNote, deleteNote as apiDeleteNote,
  getNotes, getFolders, getTrashNotes,
  restoreNote, permanentDeleteNote,
  createFolder, deleteFolder as apiDeleteFolder,
  createSchedule, deleteSchedule,
  type Note, type Schedule,
} from '../api';
import { resolveNoteId as resolveNoteIdFromList } from '../utils/notes';

type BooleanLike = boolean | 'true' | 'false';

type ScheduleActionPayload = {
  title?: string;
  startTime?: string;
  endTime?: string;
  allDay?: BooleanLike;
  rrule?: string;
};

type AiAction =
  | { type: 'CREATE_NOTE'; title?: string; content?: string }
  | { type: 'UPDATE_NOTE'; noteId?: string; title?: string; content?: string }
  | { type: 'DELETE_NOTE'; noteId?: string }
  | { type: 'ADD_TAG'; noteId?: string; tag?: string }
  | { type: 'REMOVE_TAG'; noteId?: string; tag?: string }
  | { type: 'RESTORE_NOTE'; noteId?: string }
  | ({ type: 'CREATE_SCHEDULE' } & ScheduleActionPayload)
  | { type: 'CREATE_SCHEDULES_BATCH'; schedules?: string }
  | { type: 'PERMANENT_DELETE'; noteId?: string; title?: string }
  | { type: 'EMPTY_TRASH'; count?: number | string }
  | { type: 'DELETE_SCHEDULE'; scheduleId?: string }
  | { type: 'DELETE_FOLDER'; folderId?: string }
  | { type: 'UNKNOWN'; originalType: string };

function asRecord(value: unknown): Record<string, unknown> | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  return value as Record<string, unknown>;
}

function asOptionalString(value: unknown): string | undefined {
  return typeof value === 'string' ? value : undefined;
}

function asOptionalBooleanLike(value: unknown): BooleanLike | undefined {
  if (typeof value === 'boolean') return value;
  if (value === 'true' || value === 'false') return value;
  return undefined;
}

function normalizeAiAction(value: unknown): AiAction | null {
  const record = asRecord(value);
  const type = asOptionalString(record?.type);
  if (!record || !type) return null;

  switch (type) {
    case 'CREATE_NOTE':
      return { type, title: asOptionalString(record.title), content: asOptionalString(record.content) };
    case 'UPDATE_NOTE':
      return {
        type,
        noteId: asOptionalString(record.noteId),
        title: asOptionalString(record.title),
        content: asOptionalString(record.content),
      };
    case 'DELETE_NOTE':
    case 'RESTORE_NOTE':
    case 'PERMANENT_DELETE':
      return { type, noteId: asOptionalString(record.noteId), title: asOptionalString(record.title) } as AiAction;
    case 'ADD_TAG':
    case 'REMOVE_TAG':
      return { type, noteId: asOptionalString(record.noteId), tag: asOptionalString(record.tag) } as AiAction;
    case 'CREATE_SCHEDULE':
      return {
        type,
        title: asOptionalString(record.title),
        startTime: asOptionalString(record.startTime),
        endTime: asOptionalString(record.endTime),
        allDay: asOptionalBooleanLike(record.allDay),
        rrule: asOptionalString(record.rrule),
      };
    case 'CREATE_SCHEDULES_BATCH':
      return { type, schedules: asOptionalString(record.schedules) };
    case 'EMPTY_TRASH':
      return {
        type,
        count: typeof record.count === 'number' || typeof record.count === 'string' ? record.count : undefined,
      };
    case 'DELETE_SCHEDULE':
      return { type, scheduleId: asOptionalString(record.scheduleId) };
    case 'DELETE_FOLDER':
      return { type, folderId: asOptionalString(record.folderId) };
    default:
      return { type: 'UNKNOWN', originalType: type };
  }
}

function parseScheduleList(raw: string | undefined): ScheduleActionPayload[] {
  if (!raw) return [];
  const parsed: unknown = JSON.parse(raw);
  if (!Array.isArray(parsed)) return [];
  return parsed.map(normalizeSchedulePayload).filter((item): item is ScheduleActionPayload => Boolean(item));
}

function normalizeSchedulePayload(value: unknown): ScheduleActionPayload | null {
  const record = asRecord(value);
  if (!record) return null;
  return {
    title: asOptionalString(record.title),
    startTime: asOptionalString(record.startTime),
    endTime: asOptionalString(record.endTime),
    allDay: asOptionalBooleanLike(record.allDay),
    rrule: asOptionalString(record.rrule),
  };
}

export function useActions() {
  const noteStore = useNoteStore();
  const ui = useUiStore();
  const ai = useAiStore();
  const scheduleStore = useScheduleStore();

  const resolveNoteId = useCallback((noteIdOrTitle: string | undefined): string | undefined => {
    if (!noteIdOrTitle) return undefined;
    return resolveNoteIdFromList(useNoteStore.getState().notes, noteIdOrTitle);
  }, []);

  const getActionDescription = useCallback((action: AiAction): string => {
    const notes = useNoteStore.getState().notes;
    const trashNotes = useNoteStore.getState().trashNotes;
    switch (action.type) {
      case 'CREATE_NOTE':
        return `创建笔记「${action.title || '无标题'}」`;
      case 'UPDATE_NOTE': {
        const note = notes.find(n => n.id === action.noteId);
        return `更新笔记「${note?.title || '未知'}」`;
      }
      case 'DELETE_NOTE': {
        const note = notes.find(n => n.id === action.noteId);
        return `删除笔记「${note?.title || '未知'}」`;
      }
      case 'ADD_TAG': {
        const note = notes.find(n => n.id === action.noteId);
        return `给「${note?.title || '未知'}」添加标签「${action.tag}」`;
      }
      case 'REMOVE_TAG': {
        const note = notes.find(n => n.id === action.noteId);
        return `从「${note?.title || '未知'}」移除标签「${action.tag}」`;
      }
      case 'RESTORE_NOTE': {
        const note = trashNotes.find(n => n.id === action.noteId);
        return `恢复笔记「${note?.title || '未知'}」`;
      }
      case 'CREATE_SCHEDULE':
        return `创建日程「${action.title}」- ${action.startTime}`;
      case 'CREATE_SCHEDULES_BATCH':
        try {
          const scheduleList = JSON.parse(action.schedules || '[]');
          return `批量创建 ${scheduleList.length} 个日程`;
        } catch {
          return `批量创建日程`;
        }
      case 'PERMANENT_DELETE':
        return `永久删除笔记「${action.title || '未知'}」（不可恢复）`;
      case 'EMPTY_TRASH':
        return `清空回收站（${action.count || '?'} 条笔记将被永久删除）`;
      case 'DELETE_SCHEDULE':
        return `删除日程`;
      case 'DELETE_FOLDER':
        return `删除文件夹`;
      case 'UNKNOWN':
        return `不支持的操作：${action.originalType}`;
      default:
        return `操作`;
    }
  }, []);

  const executeSingleAction = useCallback(async (action: AiAction) => {
    const { notes, selectedNote, setNotes, setSelectedNote, setTrashNotes, addNote, updateNoteInList, removeNote, removeTrashNote } = useNoteStore.getState();
    const { setStatus } = useUiStore.getState();
    const { setSchedules } = useScheduleStore.getState();

    switch (action.type) {
      case 'CREATE_NOTE': {
        const newNote = await createNote({
          title: action.title || '无标题',
          content: action.content || '',
          tags: [],
          folderId: null,
        });
        addNote(newNote);
        setSelectedNote(newNote);
        setStatus('AI 已创建笔记');
        break;
      }
      case 'UPDATE_NOTE': {
        const targetId = resolveNoteId(action.noteId) || selectedNote?.id;
        if (!targetId) { setStatus('找不到要更新的笔记'); break; }
        const existing = notes.find(n => n.id === targetId);
        if (!existing) { setStatus('笔记不存在'); break; }
        const updated = await updateNote(targetId, {
          title: action.title ?? existing.title,
          content: action.content ?? existing.content,
          tags: existing.tags?.map(t => t.name) || [],
          folderId: existing.folderId,
        });
        updateNoteInList(updated);
        setStatus('AI 已更新笔记');
        break;
      }
      case 'DELETE_NOTE': {
        const targetId = resolveNoteId(action.noteId) || selectedNote?.id;
        if (!targetId) { setStatus('找不到要删除的笔记'); break; }
        await apiDeleteNote(targetId);
        removeNote(targetId);
        setStatus('AI 已删除笔记（移入回收站）');
        break;
      }
      case 'ADD_TAG': {
        const targetId = resolveNoteId(action.noteId) || selectedNote?.id;
        if (!targetId || !action.tag) { setStatus('缺少笔记或标签名'); break; }
        const existing = notes.find(n => n.id === targetId);
        if (!existing) { setStatus('笔记不存在'); break; }
        const currentTags = existing.tags?.map(t => t.name) || [];
        if (!currentTags.includes(action.tag)) {
          const updated = await updateNote(targetId, {
            title: existing.title,
            content: existing.content,
            tags: [...currentTags, action.tag],
            folderId: existing.folderId,
          });
          updateNoteInList(updated);
        }
        setStatus(`AI 已添加标签「${action.tag}」`);
        break;
      }
      case 'REMOVE_TAG': {
        const targetId = resolveNoteId(action.noteId) || selectedNote?.id;
        if (!targetId || !action.tag) { setStatus('缺少笔记或标签名'); break; }
        const existing = notes.find(n => n.id === targetId);
        if (!existing) { setStatus('笔记不存在'); break; }
        const newTags = (existing.tags?.map(t => t.name) || []).filter(t => t !== action.tag);
        const updated = await updateNote(targetId, {
          title: existing.title,
          content: existing.content,
          tags: newTags,
          folderId: existing.folderId,
        });
        updateNoteInList(updated);
        setStatus(`AI 已移除标签「${action.tag}」`);
        break;
      }
      case 'RESTORE_NOTE': {
        const targetId = action.noteId;
        if (!targetId) { setStatus('缺少笔记 ID'); break; }
        await restoreNote(targetId);
        const notesData = await getNotes();
        setNotes(notesData);
        removeTrashNote(targetId);
        setStatus('AI 已从回收站恢复笔记');
        break;
      }
      case 'CREATE_SCHEDULE': {
        if (!action.title || !action.startTime) {
          setStatus('缺少日程标题或开始时间');
          break;
        }
        const created = await createSchedule({
          title: action.title,
          startTime: action.startTime,
          endTime: action.endTime,
          allDay: action.allDay === 'true' || action.allDay === true,
          rrule: action.rrule || undefined,
        });
        useScheduleStore.getState().addSchedule(created);
        setStatus(`AI 已创建日程「${action.title}」`);
        break;
      }
      case 'CREATE_SCHEDULES_BATCH': {
        try {
          const scheduleList = parseScheduleList(action.schedules);
          const createdSchedules: Schedule[] = [];
          for (const item of scheduleList) {
            if (item.title && item.startTime) {
              const created = await createSchedule({
                title: item.title,
                startTime: item.startTime,
                endTime: item.endTime,
                allDay: item.allDay === 'true' || item.allDay === true,
                rrule: item.rrule || undefined,
              });
              createdSchedules.push(created);
            }
          }
          useScheduleStore.getState().addSchedules(createdSchedules);
          setStatus(`AI 已创建 ${createdSchedules.length} 个日程`);
        } catch (err) {
          console.error('批量创建日程失败:', err);
          setStatus('批量创建日程失败');
        }
        break;
      }
      case 'PERMANENT_DELETE': {
        const targetId = action.noteId;
        if (!targetId) { setStatus('缺少笔记 ID'); break; }
        await permanentDeleteNote(targetId);
        removeNote(targetId);
        removeTrashNote(targetId);
        setStatus('已永久删除笔记');
        break;
      }
      case 'EMPTY_TRASH': {
        const trashList = await getTrashNotes();
        for (const note of trashList) {
          await permanentDeleteNote(note.id);
        }
        useNoteStore.getState().setTrashNotes([]);
        setStatus(`已清空回收站，永久删除了 ${trashList.length} 条笔记`);
        break;
      }
      case 'DELETE_SCHEDULE': {
        const scheduleId = action.scheduleId;
        if (!scheduleId) { setStatus('缺少日程 ID'); break; }
        await deleteSchedule(scheduleId);
        useScheduleStore.getState().removeSchedule(scheduleId);
        setStatus('已删除日程');
        break;
      }
      case 'DELETE_FOLDER': {
        const folderId = action.folderId;
        if (!folderId) { setStatus('缺少文件夹 ID'); break; }
        await apiDeleteFolder(folderId);
        const updatedFolders = await getFolders();
        useNoteStore.getState().setFolders(updatedFolders);
        const updatedNotes = await getNotes();
        useNoteStore.getState().setNotes(updatedNotes);
        setStatus('已删除文件夹');
        break;
      }
      case 'UNKNOWN':
        setStatus(`不支持的操作类型：${action.originalType}`);
        break;
      default:
        setStatus('不支持的操作类型');
    }
  }, [resolveNoteId]);

  const executeAiAction = useCallback(async (actionJson: string) => {
    try {
      const parsed: unknown = JSON.parse(actionJson);
      const parsedActions = Array.isArray(parsed) ? parsed : [parsed];
      const actions = parsedActions.map(normalizeAiAction).filter((action): action is AiAction => Boolean(action));
      if (actions.length === 0) {
        ui.setStatus('没有可执行的 AI 操作');
        return;
      }

      const pending = actions.map((action, index: number) => ({
        id: `action-${Date.now()}-${index}`,
        type: action.type,
        description: getActionDescription(action),
        data: action,
      }));

      ui.setPendingActions(pending);
      ui.setShowConfirmDialog(true);
    } catch (err) {
      console.error('解析 AI 操作失败:', err);
      ui.setStatus('解析 AI 操作失败：' + (err as Error).message);
    }
  }, [getActionDescription]);

  const handleConfirmActions = useCallback(async () => {
    const { pendingActions } = useUiStore.getState();
    ui.setShowConfirmDialog(false);
    ai.setAiPhase('executing');

    let successCount = 0;
    let failCount = 0;

    for (const pending of pendingActions) {
      try {
        const action = normalizeAiAction(pending.data);
        if (!action) {
          throw new Error('Invalid pending action payload');
        }
        await executeSingleAction(action);
        successCount++;
      } catch (err) {
        console.error('执行操作失败:', pending, err);
        failCount++;
      }
    }

    ui.setPendingActions([]);
    ai.setAiPhase('idle');

    if (pendingActions.length > 1) {
      ui.setStatus(`AI 已执行 ${successCount} 个操作${failCount > 0 ? `，${failCount} 个失败` : ''}`);
    } else {
      ui.setStatus('操作已完成');
    }
  }, [executeSingleAction]);

  const handleCancelActions = useCallback(() => {
    ui.setShowConfirmDialog(false);
    ui.setPendingActions([]);
    ui.setStatus('已取消操作');
  }, []);

  return {
    resolveNoteId,
    getActionDescription,
    executeSingleAction,
    executeAiAction,
    handleConfirmActions,
    handleCancelActions,
  };
}
