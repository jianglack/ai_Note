import { useCallback, useRef, useEffect, useState } from 'react';
import { useAiStore } from '../stores/aiStore';
import { useNoteStore } from '../stores/noteStore';
import { useUiStore } from '../stores/uiStore';
import { useCardStore } from '../stores/cardStore';
import {
  aiChat,
  smartChat,
  getChatHistory,
  getSpiritGreeting,
  getSmartSuggestions,
  approvePlan,
  pausePlan,
  resumePlan,
  cancelPlan,
  rollbackPlan,
  skipStep,
  getPlanDetail,
  subscribePlanProgress,
} from '../api';
import type { AiChatResponse, ChatHistoryMessage } from '../api';
import { useWorkflowExecution } from './useWorkflowExecution';
import { isSuggestionDismissed } from './useCardActions';
import type { CardPayload } from '../components/chat/cards/types';
import type { AiMessage } from '../stores/aiStore';

const CHAT_HISTORY_PAGE_SIZE = 100;

function toAiMessages(history: ChatHistoryMessage[]): AiMessage[] {
  return history.map(msg => ({
    id: msg.id,
    content: msg.content,
    timestamp: new Date(msg.createdAt).getTime(),
    role: (msg.role === 'assistant' ? 'spirit' : 'user') as 'spirit' | 'user'
  }));
}

export function useAiChat() {
  const ai = useAiStore();
  const noteStore = useNoteStore();
  const ui = useUiStore();

  const { createWorkflowFromActions } = useWorkflowExecution();

  const abortControllerRef = useRef<AbortController | null>(null);
  const planProgressCancelRef = useRef<(() => void) | null>(null);
  const typingCancelRef = useRef(false);
  const requestSeqRef = useRef(0);
  const chatHistoryCursorRef = useRef<string | null>(null);
  const chatHistoryLoadingOlderRef = useRef(false);
  const [hasMoreHistory, setHasMoreHistory] = useState(false);
  const [isLoadingOlderHistory, setIsLoadingOlderHistory] = useState(false);

  useEffect(() => {
    return () => {
      planProgressCancelRef.current?.();
      planProgressCancelRef.current = null;
    };
  }, []);

  // 加载 AI 对话历史并显示欢迎问候语 + 自动加载智能建议卡片
  const loadChatHistory = useCallback(async () => {
    try {
      const historyPage = await getChatHistory({ limit: CHAT_HISTORY_PAGE_SIZE });
      const loadedMessages = toAiMessages(historyPage.items);
      chatHistoryCursorRef.current = historyPage.nextCursor ?? null;
      setHasMoreHistory(Boolean(historyPage.hasMore && historyPage.nextCursor));

      // 如果没有历史消息，显示欢迎问候语
      if (loadedMessages.length === 0) {
        const greeting = await getSpiritGreeting();
        const greetingMsg = {
          id: 'greeting-' + Date.now(),
          content: greeting,
          timestamp: Date.now(),
          role: 'spirit' as const
        };
        ai.setMessages([greetingMsg]);
      } else {
        ai.setMessages(loadedMessages);
      }

      // 自动加载智能建议 → 渲染为卡片消息
      loadSmartSuggestions();
    } catch (err) {
      console.error('加载对话历史失败', err);
      chatHistoryCursorRef.current = null;
      setHasMoreHistory(false);
      try {
        const greeting = await getSpiritGreeting();
        const greetingMsg = {
          id: 'greeting-' + Date.now(),
          content: greeting,
          timestamp: Date.now(),
          role: 'spirit' as const
        };
        ai.setMessages([greetingMsg]);
        loadSmartSuggestions();
      } catch (greetErr) {
        console.error('获取问候语失败', greetErr);
      }
    }
  }, []);

  const loadOlderChatHistory = useCallback(async () => {
    const before = chatHistoryCursorRef.current;
    if (!before || chatHistoryLoadingOlderRef.current) return;

    chatHistoryLoadingOlderRef.current = true;
    setIsLoadingOlderHistory(true);
    try {
      const historyPage = await getChatHistory({ limit: CHAT_HISTORY_PAGE_SIZE, before });
      const olderMessages = toAiMessages(historyPage.items);
      const currentMessages = useAiStore.getState().messages;
      const existingIds = new Set(currentMessages.map(message => message.id));
      const uniqueOlderMessages = olderMessages.filter(message => !existingIds.has(message.id));

      if (uniqueOlderMessages.length > 0) {
        useAiStore.getState().setMessages([...uniqueOlderMessages, ...currentMessages]);
      }
      chatHistoryCursorRef.current = historyPage.nextCursor ?? null;
      setHasMoreHistory(Boolean(historyPage.hasMore && historyPage.nextCursor));
    } catch (err) {
      console.error('加载更早对话历史失败', err);
    } finally {
      chatHistoryLoadingOlderRef.current = false;
      setIsLoadingOlderHistory(false);
    }
  }, []);

  // 从后端拉取智能建议，转成卡片消息
  const loadSmartSuggestions = useCallback(async () => {
    try {
      const suggestions = await getSmartSuggestions();
      if (!suggestions || suggestions.length === 0) return;

      for (const s of suggestions) {
        if (!s.card || !s.card.kind) continue;
        // 跳过已被用户处理过的建议（localStorage 持久化）
        const suggestionText = s.card.text || s.message;
        if (isSuggestionDismissed(suggestionText)) continue;
        const cardId = 'suggestion-' + Date.now() + '-' + Math.random().toString(36).slice(2, 6);
        const cardPayload: CardPayload = {
          kind: 'suggestion',
          suggestionKind: (s.card.suggestionKind || 'review') as 'review' | 'expired' | 'merge',
          text: s.card.text || s.message,
          buttons: (s.card.buttons || []).map(b => ({
            label: b.label,
            variant: b.variant as 'primary' | 'subtle' | undefined,
            action: b.action,
          })),
        };
        useCardStore.getState().createCard(cardId, cardPayload);
        ai.addMessage({
          id: 'suggestion-msg-' + Date.now() + '-' + Math.random().toString(36).slice(2, 6),
          content: '',
          timestamp: Date.now(),
          role: 'spirit',
          cardId,
        });
      }
    } catch (err) {
      console.error('加载智能建议失败', err);
    }
  }, []);

  const handleCancelAi = useCallback(() => {
    typingCancelRef.current = true;
    abortControllerRef.current?.abort();
    ai.setIsTyping(false);
    ai.setAiPhase('idle');
    ai.setCurrentMessage('');
    ai.setAiSteps([]);
  }, []);

  const handleAiMessage = useCallback(async (message: string) => {
    // 取消前一次（如果有）
    abortControllerRef.current?.abort();
    typingCancelRef.current = false;
    const abortController = new AbortController();
    abortControllerRef.current = abortController;
    const currentSeq = ++requestSeqRef.current;

    try {
      // 添加用户消息
      const userMsg = {
        id: Date.now().toString(),
        content: message,
        timestamp: Date.now(),
        role: 'user' as const,
        sources: undefined
      };
      ai.addMessage(userMsg);

      ai.setIsTyping(true);
      ai.setAiPhase('thinking');
      ai.setCurrentMessage('');


      // 所有消息统一走后端 Agent，由后端决定是否检索、调用工具或返回确认卡片。
      ai.setAiSteps([]);
      const selectedNoteIds = noteStore.selectedNote ? [noteStore.selectedNote.id] : [];

      try {
        ai.addAiStep({ step: 'routing', detail: '正在判断处理方式...' });
        const smartResponse = await smartChat(message, selectedNoteIds, false);

        if (abortController.signal.aborted || requestSeqRef.current !== currentSeq) return;

        if (smartResponse.type === 'plan_created' && smartResponse.plan) {
          ai.addMessage({
            id: (Date.now() + 1).toString(),
            content: `我已将这个请求识别为需要计划审批的任务。${smartResponse.routeDecision?.reason || ''}`,
            timestamp: Date.now(),
            role: 'spirit',
            sources: smartResponse.sources,
            planData: smartResponse.plan,
          });
          ai.setCurrentMessage('');
          ai.setIsTyping(false);
          ai.setAiPhase('idle');
          ai.setAiSteps([]);
          return;
        }

        const finalResponse: AiChatResponse = {
          content: smartResponse.content || '抱歉，处理请求时出错了',
          sources: smartResponse.sources || {},
          action: smartResponse.actionJson,
        };
        const displayContent = finalResponse.content;

        // Check if response contains pending actions
        let workflowCardId: string | undefined;
        let pendingAction: import('../stores/aiStore').PendingActionInfo | undefined;
        if (finalResponse?.action) {
          try {
            const parsed = JSON.parse(finalResponse.action);
            const singleCandidate =
              Array.isArray(parsed) && parsed.length === 1
                ? parsed[0]
                : !Array.isArray(parsed)
                  ? parsed
                  : null;
            const actionObject =
              singleCandidate && typeof singleCandidate === 'object'
                ? singleCandidate as Record<string, unknown>
                : null;

            const backendTypeMap: Record<string, string> = {
              DELETE_NOTE: 'deleteNote',
              DELETE_NOTES: 'deleteNotes',
              PERMANENT_DELETE: 'permanentDeleteNote',
              EMPTY_TRASH: 'emptyTrash',
              DELETE_FOLDER: 'deleteFolder',
              DELETE_SCHEDULE: 'deleteSchedule',
            };
            const actionDescMap: Record<string, string> = {
              deleteNote: '删除笔记',
              deleteNotes: '删除全部笔记',
              updateNote: '更新笔记',
              createNote: '创建笔记',
              moveNote: '移动笔记',
              permanentDeleteNote: '永久删除笔记',
              emptyTrash: '清空回收站',
              deleteFolder: '删除文件夹',
              createSchedule: '创建日程',
              updateSchedule: '更新日程',
              deleteSchedule: '删除日程',
            };

            const rawAction = typeof actionObject?.action === 'string' ? actionObject.action : undefined;
            const rawType = typeof actionObject?.type === 'string' ? actionObject.type : undefined;
            const normalizedAction = rawAction || (rawType ? backendTypeMap[rawType] : undefined);

            if (actionObject && normalizedAction) {
              const title = typeof actionObject.title === 'string' ? actionObject.title : undefined;
              const folder = typeof actionObject.folder === 'string' ? actionObject.folder : undefined;
              const count = actionObject.count != null ? String(actionObject.count) : undefined;
              const details: { label: string; warn?: boolean }[] = [];
              if (folder) details.push({ label: `文件夹：${folder}` });
              if (normalizedAction === 'deleteNote' || normalizedAction === 'deleteNotes') details.push({ label: '删除后进入回收站，可恢复' });
              if (count) details.push({ label: `共 ${count} 条`, warn: true });

              const desc = title
                ? `${actionDescMap[normalizedAction] || normalizedAction}《${title}》`
                : actionDescMap[normalizedAction] || normalizedAction;
              const irreversibleActions = ['permanentDeleteNote', 'emptyTrash', 'deleteFolder', 'deleteSchedule'];
              pendingAction = {
                actionType: normalizedAction,
                actionDescription: desc,
                actionJson: finalResponse.action,
                details: details.length > 0 ? details : undefined,
                irreversible: irreversibleActions.includes(normalizedAction),
              };
            } else {
              // Array of actions → batch workflow card
              const cardId = createWorkflowFromActions(finalResponse.action);
              if (cardId) workflowCardId = cardId;
            }
          } catch {
            // Fallback: treat as workflow card
            const cardId = createWorkflowFromActions(finalResponse.action);
            if (cardId) workflowCardId = cardId;
          }
        }

        // Check if response contains interactive card instructions
        let msgCardId: string | undefined;
        let cleanContent = displayContent;
        const cardMatch = displayContent.match(/INTERACTIVE_CARD:(\{[\s\S]*?\})(?:\n|$)/);
        if (cardMatch) {
          try {
            const cardPayload = JSON.parse(cardMatch[1]) as CardPayload;
            const cid = 'card-' + Date.now();
            useCardStore.getState().createCard(cid, cardPayload);
            msgCardId = cid;
            cleanContent = displayContent.replace(cardMatch[0], '').trim();
          } catch (e) {
            console.warn('Failed to parse card payload:', e);
          }
        }

        const aiMsg = {
          id: (Date.now() + 1).toString(),
          content: cleanContent,
          timestamp: Date.now(),
          role: 'spirit' as const,
          sources: finalResponse?.sources,
          degraded: finalResponse?.degraded,
          chatMode: finalResponse?.chatMode,
          degradationReason: finalResponse?.degradationReason,
          workflowCardId,
          cardId: msgCardId,
          pendingAction,
        };
        ai.addMessage(aiMsg);
        ai.setCurrentMessage('');
        ai.setIsTyping(false);
        ai.setAiPhase('idle');
        ai.setAiSteps([]);

        // Agent 操作后刷新 UI 数据（only if no workflow card — card handles its own refresh）
        if (!workflowCardId) {
          noteStore.loadData();
        }
      } catch (err) {
        console.error('Agent 流式对话失败:', err);
        ai.setCurrentMessage('');

        // 将技术性错误信息转为用户友好的提示
        const rawMsg = (err as Error).message || '';
        let friendlyMsg: string;
        if (rawMsg.includes('network') || rawMsg.includes('Network') || rawMsg.includes('fetch') || rawMsg.includes('Failed to fetch')) {
          friendlyMsg = '网络连接中断，请检查网络后重试。';
        } else if (rawMsg.includes('timeout') || rawMsg.includes('Timeout') || rawMsg.includes('aborted')) {
          friendlyMsg = '请求超时，请稍后重试。';
        } else if (rawMsg.includes('500') || rawMsg.includes('Internal Server')) {
          friendlyMsg = '服务器内部错误，请稍后重试。';
        } else {
          friendlyMsg = '抱歉，处理请求时遇到问题，请稍后重试。';
        }

        const aiMsg = {
          id: (Date.now() + 1).toString(),
          content: friendlyMsg,
          timestamp: Date.now(),
          role: 'spirit' as const,
          sources: undefined
        };
        ai.addMessage(aiMsg);
        ai.setIsTyping(false);
        ai.setAiPhase('idle');
        ai.setAiSteps([]);

        // 即使出错也刷新数据，因为操作可能已经部分完成
        noteStore.loadData();
      }

    } catch (err) {
      if ((err as Error).name === 'AbortError' || abortControllerRef.current?.signal.aborted) return;
      console.error('AI 处理错误:', err);
      ui.setStatus((err as Error).message);

      const errorMsg = '抱歉，我遇到了一些问题，请稍后再试。';
      ai.setCurrentMessage(errorMsg);
      await new Promise((resolve) => setTimeout(resolve, 800));
      if (!typingCancelRef.current) {
        const aiMsg = {
          id: (Date.now() + 1).toString(),
          content: errorMsg,
          timestamp: Date.now(),
          role: 'spirit' as const,
          sources: undefined
        };
        ai.addMessage(aiMsg);
      }
      ai.setCurrentMessage('');
      ai.setIsTyping(false);
      ai.setAiPhase('idle');
      ai.setAiSteps([]);
    }
  }, [noteStore.notes, noteStore.trashNotes, noteStore.selectedNote]);

  const handleAiInlineAction = useCallback(async (action: 'summarize' | 'improve' | 'expand' | 'simplify', selectedText: string) => {
    try {
      let prompt = '';
      switch (action) {
        case 'summarize':
          prompt = `请总结以下内容：\n\n${selectedText}`;
          break;
        case 'improve':
          prompt = `请改进以下内容，使其更清晰、更专业：\n\n${selectedText}`;
          break;
        case 'expand':
          prompt = `请扩展以下内容，添加更多细节和解释：\n\n${selectedText}`;
          break;
        case 'simplify':
          prompt = `请简化以下内容，使其更易理解：\n\n${selectedText}`;
          break;
      }

      ui.setStatus('AI 处理中...');
      const response = await aiChat(prompt, 'selected', noteStore.selectedNote?.id);

      // 将 AI 响应插入到内容中
      const newContent = noteStore.selectedNote?.content + '\n\n---\n\n**AI ' +
        (action === 'summarize' ? '总结' : action === 'improve' ? '改进' : action === 'expand' ? '扩展' : '简化') +
        '：**\n\n' + response.content;
      
      if (noteStore.selectedNote) {
        noteStore.updateNoteContent(noteStore.selectedNote.id, newContent);
      }
      
      ui.setStatus('AI 处理完成');
    } catch (err) {
      ui.setStatus('AI 处理失败：' + (err as Error).message);
    }
  }, [noteStore.selectedNote]);

  /**
   * 处理计划操作（批准、暂停、恢复、取消、回滚、跳过步骤）
   */
  const handlePlanAction = useCallback(async (action: string, planId: string, stepId?: string) => {
    try {
      switch (action) {
        case 'approve':
          await approvePlan(planId);
          // 订阅进度更新
          planProgressCancelRef.current?.();
          planProgressCancelRef.current = subscribePlanProgress(
            planId,
            (data) => {
              // 刷新计划详情
              getPlanDetail(planId).then(plan => ai.updatePlanInList(plan)).catch(() => {});
            },
            (data) => {
              getPlanDetail(planId).then(plan => ai.updatePlanInList(plan)).catch(() => {});
              if (data.status === 'COMPLETED' || data.status === 'FAILED') {
                noteStore.loadData();
                planProgressCancelRef.current?.();
                planProgressCancelRef.current = null;
              }
            }
          );
          // 立即刷新一次
          const approvedPlan = await getPlanDetail(planId);
          ai.updatePlanInList(approvedPlan);
          break;
        case 'pause':
          await pausePlan(planId);
          break;
        case 'resume':
          await resumePlan(planId);
          break;
        case 'cancel':
          await cancelPlan(planId);
          break;
        case 'rollback':
          await rollbackPlan(planId);
          break;
        case 'skipStep':
          if (stepId) await skipStep(planId, stepId);
          break;
      }
      // 刷新计划状态
      const updated = await getPlanDetail(planId);
      ai.updatePlanInList(updated);
    } catch (err) {
      console.error('Plan action failed:', err);
      ai.addMessage({
        id: Date.now().toString(),
        content: `操作失败: ${(err as Error).message}`,
        timestamp: Date.now(),
        role: 'spirit',
      });
    }
  }, []);

  return {
    loadChatHistory,
    loadOlderChatHistory,
    hasMoreHistory,
    isLoadingOlderHistory,
    handleCancelAi,
    handleAiMessage,
    handleAiInlineAction,
    handlePlanAction,
  };
}
