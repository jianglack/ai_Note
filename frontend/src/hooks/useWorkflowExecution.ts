import { useCallback } from 'react';
import { useWorkflowStore, type OperationType } from '../stores/workflowStore';
import { useToastStore } from '../stores/toastStore';
import { useNoteStore } from '../stores/noteStore';
import { useAiStore } from '../stores/aiStore';
import {
  batchDeleteNotes,
  batchPermanentDeleteNotes,
  batchRestoreNotes,
  batchMoveNotes,
  batchAddTag,
  batchRemoveTag,
  batchArchiveNotes,
  getNotes,
  getTrashNotes,
  type BatchResult,
} from '../api';

// Map action type from Agent → workflow operation type
function mapActionType(actionType: string): OperationType | null {
  switch (actionType) {
    case 'DELETE_NOTE': return 'delete';
    case 'PERMANENT_DELETE': return 'permanent_delete';
    case 'RESTORE_NOTE': return 'restore';
    case 'MOVE_NOTE': return 'move';
    case 'ADD_TAG': return 'tag';
    case 'REMOVE_TAG': return 'remove_tag';
    case 'EMPTY_TRASH': return 'permanent_delete';
    default: return null;
  }
}

export function useWorkflowExecution() {
  const workflowStore = useWorkflowStore();
  const toastStore = useToastStore();
  const noteStore = useNoteStore();
  const aiStore = useAiStore();

  /**
   * Create a workflow card from Agent pending actions.
   * Returns cardId if workflow card was created, null if should fall back to old flow.
   */
  const createWorkflowFromActions = useCallback((actionJson: string): string | null => {
    try {
      const parsed = JSON.parse(actionJson);
      const actions: any[] = Array.isArray(parsed) ? parsed : [parsed];

      if (actions.length === 0) return null;

      // Determine operation type from first action
      const opType = mapActionType(actions[0].type);
      if (!opType) return null; // Unknown action type, fall back to old flow

      // Build items list
      const allNotes = useNoteStore.getState().notes;
      const trashNotes = useNoteStore.getState().trashNotes;

      // Handle EMPTY_TRASH specially — all trash notes
      if (actions[0].type === 'EMPTY_TRASH') {
        if (trashNotes.length === 0) return null;
        const items = trashNotes.map((n) => ({
          noteId: n.id,
          title: n.title || '无标题',
          folder: undefined,
        }));
        const cardId = workflowStore.createCard('permanent_delete', items);
        workflowStore.toConfirmation(cardId);
        return cardId;
      }

      // Build items from action noteIds
      const items = actions
        .filter((a) => a.noteId)
        .map((a) => {
          const note = allNotes.find((n) => n.id === a.noteId) || trashNotes.find((n) => n.id === a.noteId);
          return {
            noteId: a.noteId,
            title: a.title || note?.title || '未知笔记',
            folder: undefined,
          };
        });

      if (items.length === 0) return null;

      const cardId = workflowStore.createCard(opType, items, {
        tagName: actions[0].tag || actions[0].tagName,
        folderId: actions[0].folderId,
      });

      workflowStore.toConfirmation(cardId);
      return cardId;
    } catch (err) {
      console.error('Failed to create workflow card:', err);
      return null;
    }
  }, []);

  /**
   * Execute the confirmed workflow card by calling batch APIs.
   */
  const executeWorkflowCard = useCallback(async (cardId: string) => {
    const card = useWorkflowStore.getState().cards[cardId];
    if (!card) return;

    const selectedItems = card.items.filter((i) => i.selected);
    if (selectedItems.length === 0) return;

    workflowStore.confirm(cardId);
    aiStore.setAiPhase('executing');

    const noteIds = selectedItems.map((i) => i.noteId);
    const params = card.params || {};

    try {
      let result: BatchResult;

      switch (card.operationType) {
        case 'delete':
          result = await batchDeleteNotes(noteIds);
          break;
        case 'permanent_delete':
          result = await batchPermanentDeleteNotes(noteIds);
          break;
        case 'restore':
          result = await batchRestoreNotes(noteIds);
          break;
        case 'move':
          result = await batchMoveNotes(noteIds, params.folderId || '');
          break;
        case 'tag':
          result = await batchAddTag(noteIds, params.tagName || '');
          break;
        case 'remove_tag':
          result = await batchRemoveTag(noteIds, params.tagName || '');
          break;
        case 'archive':
          result = await batchArchiveNotes(noteIds, true);
          break;
        default:
          throw new Error('Unknown operation: ' + card.operationType);
      }

      // Update item statuses from result details
      for (const detail of result.details) {
        workflowStore.updateItemStatus(
          cardId,
          detail.noteId,
          detail.status === 'success' ? 'success' : 'failed',
          detail.error,
        );
      }

      workflowStore.updateProgress(cardId, result.successCount + result.failedCount);
      workflowStore.complete(cardId, {
        successCount: result.successCount,
        failedCount: result.failedCount,
      });

      // Show toast
      const updatedCard = useWorkflowStore.getState().cards[cardId];
      if (updatedCard?.state === 'done') {
        toastStore.addToast({
          type: 'success',
          title: `${result.successCount} 篇笔记已${card.operationLabel}`,
        });
      } else if (updatedCard?.state === 'partial_failed') {
        toastStore.addToast({
          type: 'partial',
          title: `成功 ${result.successCount} 篇，失败 ${result.failedCount} 篇`,
        });
      } else {
        toastStore.addToast({
          type: 'failed',
          title: `操作失败`,
          message: `${result.failedCount} 篇笔记未能${card.operationLabel}`,
        });
      }

      // Refresh note list
      try {
        const [notes, trash] = await Promise.all([getNotes(), getTrashNotes()]);
        noteStore.setNotes(notes);
        noteStore.setTrashNotes(trash);
      } catch {
        // Silent refresh failure
      }
    } catch (err) {
      console.error('Workflow execution failed:', err);
      // Mark all items as failed
      for (const item of selectedItems) {
        workflowStore.updateItemStatus(cardId, item.noteId, 'failed', (err as Error).message);
      }
      workflowStore.complete(cardId, { successCount: 0, failedCount: selectedItems.length });
      toastStore.addToast({
        type: 'failed',
        title: '操作失败',
        message: (err as Error).message,
      });
    } finally {
      aiStore.setAiPhase('idle');
    }
  }, []);

  /**
   * Retry failed items in a workflow card.
   */
  const retryWorkflowCard = useCallback(async (cardId: string) => {
    workflowStore.retryFailed(cardId);
    await executeWorkflowCard(cardId);
  }, [executeWorkflowCard]);

  return {
    createWorkflowFromActions,
    executeWorkflowCard,
    retryWorkflowCard,
  };
}
